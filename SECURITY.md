# Security Policy

## Reporting a Vulnerability

The Apache ManifoldCF PMC takes security seriously. If you discover a potential security issue in Apache ManifoldCF, please report it privately to the Apache Security Team at **security@apache.org**.

Please **do not** report security issues via public GitHub issues, Jira tickets, pull requests, or public mailing lists.

For details on the Apache Software Foundation vulnerability handling process, see the [ASF Security Team documentation](https://www.apache.org/security/).

## Supported Versions

Security fixes are applied to the active development branch (`trunk`) and released in the latest 2.x release line. Users are encouraged to upgrade to the most recent 2.x release.

## Security Model and Trust Boundaries

Apache ManifoldCF is an enterprise content crawling and indexing framework composed of several web applications and connectors with distinct trust boundaries:

### 1. Crawler-UI Administrative Console (`/mcf-crawler-ui`)

- The Crawler-UI is an authenticated administrative console for managing connections, authorities, mappers, jobs, and crawl state.
- **Operator responsibilities:**
  - Change the default `admin` / `admin` credentials (`org.apache.manifoldcf.login.name` and `org.apache.manifoldcf.login.password` in `properties.xml`) before deployment.
  - Deploy behind TLS (HTTPS) and restrict network access to authorized administrators.
- **In-scope protections:**
  - Authentication is required for all administrative views and actions.
  - State-changing operations require HTTP `POST` and a valid per-session request token so cross-site forged requests from an administrator's browser are rejected even in firewalled deployments.

### 2. REST API Service (`/mcf-api-service`, `/api/*`)

- The REST API provides programmatic administrative access equivalent to the Crawler-UI.
- **Operator responsibilities:**
  - Configure strong API credentials (`org.apache.manifoldcf.apilogin.name` and `org.apache.manifoldcf.apilogin.password`, which default to the UI login credentials if not set separately).
  - Deploy behind TLS and restrict network access to trusted automation and administrative clients.
- **In-scope protections:**
  - Authentication is required for all endpoints (via HTTP Basic Authentication or `/json/LOGIN` session); empty credentials are rejected.
  - Obfuscated connection parameters (such as downstream passwords and secrets) are masked in API read responses.

### 3. Authority Service (`/mcf-authority-service`, `/UserACLs`)

- The Authority Service is designed as an **internal backchannel** queried by search-engine authorization plugins (such as Apache Solr, Elasticsearch, or OpenSearch) to retrieve access tokens for a user.
- By default, `/UserACLs` does not enforce servlet-level authentication so search engines on a private network can query it directly.
- **Operator responsibilities:**
  - **Network-restrict** the Authority Service so that only trusted search-engine hosts can reach `/UserACLs`, or enable container-level authentication (for example using the `web.xml.external` deployment descriptor or an authenticating reverse proxy).
- **In-scope protections:**
  - Even on internal networks, user identifiers passed to `/UserACLs` are treated as untrusted data when constructing backend directory, HTTP, or database queries (for example, LDAP filters are escaped per RFC 4515).

### 4. Connectors and External Data Sources

- Repository, authority, transformation, notification, and output connectors communicate with external systems whose responses may be untrusted, compromised, or tampered with in transit.
- **In-scope protections:**
  - Connectors harden XML parsers against external entity resolution and DTD processing, and escape external inputs used in query languages.

### 5. Credential Storage and Obfuscation

- Connector secrets stored via `ConfigParams.setObfuscatedParameter(...)` use reversible obfuscation.
- **Operator responsibilities:**
  - Built-in default obfuscation is intended only to avoid casual plaintext exposure and does not provide strong cryptographic encryption-at-rest unless a deployment-specific key and salt are configured via `org.apache.manifoldcf.obfuscation.passcode` and `org.apache.manifoldcf.obfuscation.salt` (or the `MCF_OBFUSCATION_PASSCODE` and `MCF_OBFUSCATION_SALT` environment variables).
  - Protect access to `properties.xml`, the ManifoldCF database, and host filesystem permissions accordingly.
