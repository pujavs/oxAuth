/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.idgen.ws.rs;

import java.util.List;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang.StringUtils;
import org.gluu.site.ldap.persistence.LdapEntryManager;
import org.slf4j.Logger;
import org.xdi.ldap.model.LdapDummyEntry;
import org.xdi.oxauth.model.common.IdType;
import org.xdi.oxauth.model.config.BaseDnConfiguration;
import org.xdi.oxauth.model.config.StaticConfiguration;
import org.xdi.oxauth.service.api.IdGenerator;
import org.xdi.util.INumGenerator;

import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.Filter;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.RDN;

/**
 * Inum ID generator. Generates inum: e.g. @!1111!0001!1234.
 *
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 26/06/2013
 */
@Stateless
@Named("inumGenerator")
public class InumGenerator {

    public static final String SEPARATOR = "!";

    private static final int MAX = 100;

    @Inject
    private Logger log;
    @Inject
    private LdapEntryManager ldapEntryManager;

    @Inject
    private StaticConfiguration staticConfiguration;

    public String generateId(String p_idType, String p_idPrefix) {
        final IdType idType = IdType.fromString(p_idType);
        if (idType != null) {
            return generateId(idType, p_idPrefix);
        } else {
            log.error("Unable to identify id type: {}", p_idType);
        }
        return "";
    }

    public String generateId(IdType p_idType, String p_idPrefix) {
        String inum;
        int counter = 0;

        try {
            while (true) {
                final StringBuilder sb = new StringBuilder();
                sb.append(p_idPrefix).
                        append(InumGenerator.SEPARATOR).
                        append(p_idType.getInum()).
                        append(InumGenerator.SEPARATOR);

                if ((IdType.CLIENTS == p_idType) || (IdType.PEOPLE == p_idType)) { 
                	sb.append(INumGenerator.generate(4));
                } else {
                	sb.append(INumGenerator.generate(2));
                }

                inum = sb.toString();
                if (StringUtils.isBlank(inum)) {
                    log.error("Unable to generate inum: {}", inum);
                    break;
                }

                if (!contains(inum, p_idType)) {
                    break;
                }

                /* Just to make sure it doesn't get into an infinite loop */
                if (counter > MAX) {
                    log.error("Infinite loop problem while generating new inum");
                    return "";
                }
                counter++;
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            inum = e.getMessage();
        }
        log.trace("Generated inum: {}", inum);
        return inum;
    }

    public boolean contains(String inum, IdType type) {
        final String baseDn = baseDn(type);
        try {
            final Filter filter = Filter.create(String.format("inum=%s", inum));
            final List<LdapDummyEntry> entries = ldapEntryManager.findEntries(baseDn, LdapDummyEntry.class, filter);
            return entries != null && !entries.isEmpty();
        } catch (LDAPException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    public String baseDn(IdType p_type) {
        final BaseDnConfiguration baseDn = staticConfiguration.getBaseDn();
        switch (p_type) {
            case CLIENTS:
                return baseDn.getClients();
            case APPLIANCE:
                return baseDn.getAppliance();
            case ATTRIBUTE:
                return baseDn.getAttributes();
            case PEOPLE:
                return baseDn.getPeople();
        }

        // if not able to identify baseDn by type then return organization baseDn, e.g. o=gluu
        try {
            final DN dnObj = new DN(baseDn.getClients()); // baseDn.getClients(), e.g. ou=clients,o=@!1111,o=gluu
            final RDN[] rdns = dnObj.getRDNs();
            final RDN rdn = rdns[rdns.length - 1];
            return rdn.toNormalizedString();
        } catch (LDAPException e) {
            log.error(e.getMessage(), e);
        }
        log.error("Use fallback DN: o=gluu, for ID generator, please check oxAuth configuration, clientDn must be valid DN");
        return "o=gluu";
    }

}
