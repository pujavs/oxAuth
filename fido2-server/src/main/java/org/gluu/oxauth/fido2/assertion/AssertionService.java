/*
 * Copyright (c) 2018 Mastercard
 * Copyright (c) 2018 Gluu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.gluu.oxauth.fido2.assertion;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Base64;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang.StringUtils;
import org.gluu.oxauth.fido2.model.entry.Fido2AuthenticationData;
import org.gluu.oxauth.fido2.model.entry.Fido2RegistrationData;
import org.gluu.oxauth.fido2.model.entry.Fido2RegistrationEntry;
import org.gluu.oxauth.fido2.persist.Fido2AuthenticationPersistenceService;
import org.gluu.oxauth.fido2.persist.Fido2RegistrationPersistenceService;
import org.gluu.oxauth.fido2.service.AuthenticatorAssertionVerifier;
import org.gluu.oxauth.fido2.service.ChallengeGenerator;
import org.gluu.oxauth.fido2.service.ChallengeVerifier;
import org.gluu.oxauth.fido2.service.CommonVerifiers;
import org.gluu.oxauth.fido2.service.DomainVerifier;
import org.gluu.oxauth.fido2.service.Fido2RPRuntimeException;
import org.slf4j.Logger;
import org.xdi.oxauth.model.configuration.AppConfiguration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Named
class AssertionService {

    @Inject
    private Logger log;

    @Inject
    private ChallengeVerifier challengeVerifier;

    @Inject
    private DomainVerifier domainVerifier;

    @Inject
    private Fido2RegistrationPersistenceService registrationsRepository;

    @Inject
    private Fido2AuthenticationPersistenceService authenticationsRepository;

    @Inject
    private AuthenticatorAssertionVerifier authenticatorAuthorizationVerifier;

    @Inject
    private ChallengeGenerator challengeGenerator;

    @Inject
    private ObjectMapper om;

    @Inject
    @Named("base64UrlEncoder")
    private Base64.Encoder base64UrlEncoder;

    @Inject
    @Named("base64UrlDecoder")
    private Base64.Decoder base64UrlDecoder;

    @Inject
    private CommonVerifiers commonVerifiers;

    @Inject
    private AppConfiguration appConfiguration;

    JsonNode options(JsonNode params) {
        log.info("options {}", params);
        return assertionOptions(params);
    }

    JsonNode verify(JsonNode params) {
        log.info("authenticateResponse {}", params);
        ObjectNode authenticateResponseNode = om.createObjectNode();
        JsonNode response = params.get("response");

        commonVerifiers.verifyBasicPayload(params);
        String keyId = commonVerifiers.verifyThatString(params.get("id"));
        commonVerifiers.verifyAssertionType(params.get("type"));
        commonVerifiers.verifyThatString(params.get("rawId"));
        JsonNode userHandle = params.get("response").get("userHandle");
        if (userHandle != null && params.get("response").hasNonNull("userHandle")) {
            // this can be null for U2F authenticators
            commonVerifiers.verifyThatString(userHandle);
        }

        JsonNode clientDataJSONNode;
        try {
            clientDataJSONNode = om
                    .readTree(new String(base64UrlDecoder.decode(params.get("response").get("clientDataJSON").asText()), Charset.forName("UTF-8")));
        } catch (IOException e) {
            throw new Fido2RPRuntimeException("Can't parse message");
        } catch (Exception e) {
            throw new Fido2RPRuntimeException("Invalid assertion data");
        }

        commonVerifiers.verifyClientJSON(clientDataJSONNode);
        commonVerifiers.verifyClientJSONTypeIsGet(clientDataJSONNode);

        String clientDataChallenge = clientDataJSONNode.get("challenge").asText();
        String clientDataOrigin = clientDataJSONNode.get("origin").asText();

        Fido2AuthenticationData authenticationEntity = authenticationsRepository.findByChallenge(clientDataChallenge)
                .orElseThrow(() -> new Fido2RPRuntimeException("Can't find matching request"));

        // challengeVerifier.verifyChallenge(authenticationEntity.getChallenge(),
        // challenge, clientDataChallenge);
        domainVerifier.verifyDomain(authenticationEntity.getDomain(), clientDataOrigin);

        Fido2RegistrationEntry registrationEntry = registrationsRepository.findByPublicKeyId(keyId)
                .orElseThrow(() -> new Fido2RPRuntimeException("Couldn't find the key"));

        Fido2RegistrationData registration = registrationEntry.getRegistrationData();
        authenticatorAuthorizationVerifier.verifyAuthenticatorAssertionResponse(response, registration, authenticationEntity);

        authenticationEntity.setW3cAuthenticatorAssertionResponse(response.toString());
        authenticationsRepository.save(authenticationEntity);
        registrationsRepository.save(registration);
        authenticateResponseNode.put("status", "ok");
        authenticateResponseNode.put("errorMessage", "");
        return authenticateResponseNode;
    }

    private JsonNode assertionOptions(JsonNode params) {
        log.info("assertionOptions {}", params);
        String username = params.get("username").asText();
        String userVerification = "required";

        if (params.hasNonNull("authenticatorSelection")) {
            JsonNode authenticatorSelector = params.get("authenticatorSelection");
            if (authenticatorSelector.hasNonNull("userVerification")) {
                userVerification = commonVerifiers.verifyUserVerification(authenticatorSelector.get("userVerification"));
            }
        }

        log.info("Options {} ", username);

        ObjectNode assertionOptionsResponseNode = om.createObjectNode();
        List<Fido2RegistrationEntry> registrationEntries = registrationsRepository.findAllByUsername(username);
        if (registrationEntries.isEmpty()) {
            throw new Fido2RPRuntimeException("No record of registration. Have you registered");
        }

        String challenge = challengeGenerator.getChallenge();
        assertionOptionsResponseNode.put("challenge", challenge);

        ObjectNode credentialUserEntityNode = assertionOptionsResponseNode.putObject("user");
        credentialUserEntityNode.put("name", username);

        ObjectNode publicKeyCredentialRpEntityNode = assertionOptionsResponseNode.putObject("rp");
        publicKeyCredentialRpEntityNode.put("name", "ACME Dawid");
        publicKeyCredentialRpEntityNode.put("id", appConfiguration.getIssuer());
        ArrayNode publicKeyCredentialDescriptors = assertionOptionsResponseNode.putArray("allowCredentials");

        for (Fido2RegistrationEntry registrationEntry : registrationEntries) {
            Fido2RegistrationData registration = registrationEntry.getRegistrationData();
            if (StringUtils.isEmpty(registration.getPublicKeyId())) {
                throw new Fido2RPRuntimeException("Can't find associated key. Have you registered");
            }
            ObjectNode publicKeyCredentialDescriptorNode = publicKeyCredentialDescriptors.addObject();
            publicKeyCredentialDescriptorNode.put("type", "public-key");
            ArrayNode authenticatorTransportNode = publicKeyCredentialDescriptorNode.putArray("transports");
            authenticatorTransportNode.add("usb").add("ble").add("nfc");
            publicKeyCredentialDescriptorNode.put("id", registration.getPublicKeyId());
        }

        assertionOptionsResponseNode.put("status", "ok");
        assertionOptionsResponseNode.put("userVerification", userVerification);

        String host;
        try {
            host = new URL(appConfiguration.getIssuer()).getHost();
        } catch (MalformedURLException e) {
            host = appConfiguration.getIssuer();
        }

        Fido2AuthenticationData entity = new Fido2AuthenticationData();
        entity.setUsername(username);
        entity.setChallenge(challenge);
        entity.setDomain(host);
        entity.setW3cCredentialRequestOptions(assertionOptionsResponseNode.toString());
        entity.setUserVerificationOption(userVerification);

        authenticationsRepository.save(entity);
        assertionOptionsResponseNode.put("status", "ok");
        assertionOptionsResponseNode.put("errorMessage", "");
        return assertionOptionsResponseNode;
    }
}