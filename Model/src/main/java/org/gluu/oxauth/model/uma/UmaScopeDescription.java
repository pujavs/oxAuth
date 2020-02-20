/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.gluu.oxauth.model.uma;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jboss.resteasy.annotations.providers.jaxb.IgnoreMediaTypes;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * A scope is a bounded extent of access that is possible to perform on a
 * resource set.
 * 
 * @author Yuriy Movchan
 * @author Yuriy Zabrovarnyy
 * Date: 10/03/2012
 */
@IgnoreMediaTypes("application/*+json") // try to ignore jettison as it's recommended here: http://docs.jboss.org/resteasy/docs/2.3.4.Final/userguide/html/json.html
@JsonPropertyOrder({ "name", "icon_uri" })
@JsonIgnoreProperties(ignoreUnknown = true)
@XmlRootElement()
@Schema(description = "A scope description is a JSON document")
public class UmaScopeDescription {

	@Schema(description = "A human-readable string describing the resource at length. The authorization server MAY use this description in any user interface it presents to a resource owner, for example, for resource protection monitoring or policy setting."
			, required = false)
	private String description;

	@Schema(description = "A URI for a graphic icon representing the scope. The referenced icon MAY be used by the authorization server in any user interface it presents to the resource owner."
			, required = false)
	private String iconUri;

    @Schema(description = "A human-readable string describing some scope (extent) of access. This name MAY be used by the authorization server in any user interface it presents to the resource owner."
                 , required = true)
	private String name;

	@JsonProperty(value = "description")
	@XmlElement(name = "description")
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@JsonProperty(value = "name")
	@XmlElement(name = "name")
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

    @JsonProperty(value = "icon_uri")
	@XmlElement(name = "icon_uri")
	public String getIconUri() {
		return iconUri;
	}

	public void setIconUri(String iconUri) {
		this.iconUri = iconUri;
	}

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ScopeDescription");
        sb.append("{iconUri='").append(iconUri).append('\'');
        sb.append(", name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
