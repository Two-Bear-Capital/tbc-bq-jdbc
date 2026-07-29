/*
 * Copyright 2026 Two Bear Capital
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package vc.tbc.bq.jdbc.auth;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Service account impersonation layered over another authentication type.
 *
 * <p>
 * Impersonation is the standard Google Cloud answer to "authenticate as me, act
 * as this service account": the caller signs in with their own identity, then
 * exchanges that for a short-lived token for a service account they hold
 * {@code roles/iam.serviceAccountTokenCreator} on. Nothing long-lived is
 * distributed, which is why {@code SECURITY.md} prefers it to handing out key
 * files.
 *
 * <p>
 * This is not an {@code authType} value of its own. It wraps whichever
 * {@link AuthType} the connection already resolved to, so every existing
 * authentication method composes with it:
 *
 * <pre>{@code
 * // Source is Application Default Credentials, the common case
 * jdbc:bigquery:my-project/my_dataset
 *     ?impersonateServiceAccount=etl@my-project.iam.gserviceaccount.com
 *
 * // Source is a key file, reaching the target through a delegation chain
 * jdbc:bigquery:my-project/my_dataset
 *     ?authType=SERVICE_ACCOUNT&credentials=/keys/bootstrap.json
 *     &impersonateServiceAccount=etl@my-project.iam.gserviceaccount.com
 *     &impersonateDelegates=mid1@my-project.iam.gserviceaccount.com
 * }</pre>
 *
 * <p>
 * <b>Delegation chain:</b> {@code delegates} names the intermediate service
 * accounts when the source identity cannot mint a token for the target
 * directly, but can for the first delegate, which can for the second, and so on
 * to the target. Order matters and is source-first. Most deployments leave it
 * empty.
 *
 * <p>
 * <b>Scopes:</b> the generated token is requested for
 * {@value #CLOUD_PLATFORM_SCOPE}. Unlike a service account key, an impersonated
 * credential has no fallback when it carries no scopes — the IAM
 * {@code generateAccessToken} call would be made with an empty scope list and
 * rejected — so this is requested up front rather than left to whoever consumes
 * the credential. It matches what {@code gcloud --impersonate-service-account}
 * requests.
 *
 * <p>
 * The Storage Read API path used to scope nothing, which made this the only
 * reason that path worked; it now reuses the connection's scoped credential
 * (#243), so this is no longer load-bearing for it. It stays because the scope
 * a token is minted with is this type's business, not its consumers'.
 *
 * @param source
 *            the authentication type providing the source identity, typically
 *            {@link ApplicationDefaultAuth}
 * @param targetPrincipal
 *            email of the service account to impersonate
 * @param delegates
 *            intermediate service account emails, source-first; empty for the
 *            usual direct case (immutable)
 * @since 4.0.0
 */
public record ImpersonatedAuth(AuthType source, String targetPrincipal, List<String> delegates) implements AuthType {

	/** Scope requested for the impersonated token. */
	public static final String CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

	public ImpersonatedAuth {
		Objects.requireNonNull(source, "source cannot be null");
		Objects.requireNonNull(targetPrincipal, "targetPrincipal cannot be null");
		if (targetPrincipal.isBlank()) {
			throw new IllegalArgumentException("targetPrincipal cannot be blank");
		}
		// Nesting would be a second, redundant way to express a chain, and the two
		// would not mean the same thing: a nested wrapper mints a full access token
		// at every step, where a delegation chain is what IAM is designed for.
		if (source instanceof ImpersonatedAuth) {
			throw new IllegalArgumentException(
					"source cannot itself be impersonated; use delegates for a delegation chain");
		}
		delegates = delegates == null ? List.of() : List.copyOf(delegates);
		for (String delegate : delegates) {
			if (delegate.isBlank()) {
				throw new IllegalArgumentException("delegates cannot contain a blank entry");
			}
		}
	}

	/**
	 * Convenience constructor for the direct case, with no delegation chain.
	 *
	 * @param source
	 *            the authentication type providing the source identity
	 * @param targetPrincipal
	 *            email of the service account to impersonate
	 */
	public ImpersonatedAuth(AuthType source, String targetPrincipal) {
		this(source, targetPrincipal, List.of());
	}

	@Override
	public Credentials toCredentials() throws IOException {
		Credentials sourceCredentials = source.toCredentials();
		// Every permitted AuthType returns a GoogleCredentials today, but the
		// interface method is declared to return Credentials, so this cannot be a
		// cast. A new auth type that broke the assumption should say so here rather
		// than fail with a ClassCastException on the first query.
		if (!(sourceCredentials instanceof GoogleCredentials googleCredentials)) {
			throw new IOException("Cannot impersonate " + targetPrincipal + ": source credentials of type "
					+ sourceCredentials.getClass().getName() + " are not Google credentials");
		}
		// Built here rather than through CredentialsCache: toCredentials() is the
		// "build it" half of the pair and the cache is the "reuse it" half, so
		// recursing would double-count a single connection's credential build.
		return ImpersonatedCredentials.newBuilder().setSourceCredentials(googleCredentials)
				.setTargetPrincipal(targetPrincipal).setDelegates(delegates).setScopes(List.of(CLOUD_PLATFORM_SCOPE))
				.build();
	}
}
