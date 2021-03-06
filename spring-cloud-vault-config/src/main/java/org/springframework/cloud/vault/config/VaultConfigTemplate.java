/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.vault.config;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.support.VaultResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Central class to retrieve configuration from Vault.
 * 
 * @author Mark Paluch
 * @see VaultOperations
 */
@Slf4j
public class VaultConfigTemplate implements VaultConfigOperations {

	private final VaultOperations vaultOperations;
	private final VaultProperties properties;

	/**
	 * Creates a new {@link VaultConfigTemplate}.
	 *
	 * @param vaultOperations must not be {@literal null}.
	 * @param properties must not be {@literal null}.
	 */
	public VaultConfigTemplate(VaultOperations vaultOperations,
			VaultProperties properties) {

		Assert.notNull(vaultOperations, "VaultOperations must not be null!");
		Assert.notNull(properties, "VaultProperties must not be null!");

		this.vaultOperations = vaultOperations;
		this.properties = properties;
	}

	public Map<String, String> read(final SecureBackendAccessor secureBackendAccessor) {

		Assert.notNull(secureBackendAccessor, "SecureBackendAccessor must not be null!");

		VaultResponseEntity<VaultResponse> response = vaultOperations.doWithVault(
				new VaultOperations.SessionCallback<VaultResponseEntity<VaultResponse>>() {
					@Override
					public VaultResponseEntity<VaultResponse> doWithVault(
							VaultOperations.VaultSession session) {

						return session.exchange("{backend}/{key}", HttpMethod.GET, null,
								VaultResponse.class, secureBackendAccessor.variables());
					}
				});

		log.info(String.format("Fetching config from Vault at: %s", response.getUri()));

		if (response.getStatusCode() == HttpStatus.OK) {

			Map<String, String> stringMap = toStringMap(response.getBody().getData());

			return secureBackendAccessor.transformProperties(stringMap);
		}

		if (response.getStatusCode() == HttpStatus.NOT_FOUND) {
			log.info(String.format("Could not locate PropertySource: %s",
					"key not found"));
		}
		else if (properties.isFailFast()) {
			throw new IllegalStateException(String.format(
					"Could not locate PropertySource and the fail fast property is set, failing Status %d %s",
					response.getStatusCode().value(), response.getMessage()));
		}
		else {
			log.warn(String.format("Could not locate PropertySource: Status %d %s",
					response.getStatusCode().value(), response.getMessage()));
		}

		return Collections.emptyMap();
	}

	private Map<String, String> toStringMap(Map<String, Object> data) {

		Map<String, String> result = new HashMap<>();
		for (String key : data.keySet()) {
			Object value = data.get(key);

			if (value != null) {
				result.put(key, value.toString());
			}
		}

		return result;
	}
}
