/*
 * Creates the realm Harbor authenticates against, over Keycloak's admin REST API.
 *
 * This rather than a realm export imported with --import-realm: an export is a
 * RealmRepresentation that has to be exactly right or the container refuses to boot,
 * and it tells you so through a Jackson field error rather than anything about
 * Keycloak. Here a wrong field is an HTTP 400 that names it, and everything below is
 * a documented API call.
 *
 * Idempotent, because `./run.sh env up` runs against a Keycloak that may already have
 * all of this. Nothing prompts — there is nobody to answer inside a container. Each
 * step GETs what it is about to create and returns early if it is already there.
 *
 * Every value here is a laptop's. The client secret is in version control and the
 * redirect URI accepts any localhost port, which is what lets the integration tests
 * work on a random one. A deployment configures its own realm and shares nothing with
 * this file.
 */

const keycloakUrl = process.env.KC_URL;
const adminUsername = process.env.KC_ADMIN_USERNAME;
const adminPassword = process.env.KC_ADMIN_PASSWORD;

const realmName = 'harbor';
const clientId = 'harbor';
const clientSecret = 'harbor-dev-secret';

/*
 * Pinned rather than generated, because this is the `sub` claim in every token and so
 * the owner_id of every row this reader writes. Recreate Keycloak with a generated id
 * and the reader comes back as somebody else, leaving the development library on disk
 * and invisible. Pinning it means `docker rm harbor-dev-keycloak` costs nothing.
 *
 * Nothing outside this file depends on the value. The test suite runs its own Keycloak
 * and chooses its own id.
 */
const readerId = '9f6b6a1c-2d4e-4f80-9a3b-5c7d8e1f0a24';
const readerUsername = 'reader';
const readerPassword = 'reader';

const realmUrl = `${keycloakUrl}/admin/realms/${realmName}`;

/*
 * Fetched per call rather than once. Creating a realm and then reaching into it is
 * more work than the default token lifetime is long, and a 401 halfway through reads
 * as a permissions problem rather than an expiry.
 */
const authorizationHeader = async () => {
    const response = await fetch(`${keycloakUrl}/realms/master/protocol/openid-connect/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
            username: adminUsername,
            password: adminPassword,
            grant_type: 'password',
            client_id: 'admin-cli'
        })
    });

    if (!response.ok) {
        console.error('Could not authenticate against Keycloak:', await response.text());
        process.exit(1);
    }

    return {
        Authorization: `Bearer ${(await response.json()).access_token}`,
        'Content-Type': 'application/json'
    };
};

const create = async (url, body, describe) => {
    const response = await fetch(url, {
        method: 'POST',
        headers: await authorizationHeader(),
        body: JSON.stringify(body)
    });

    if (!response.ok) {
        console.error(`Failed to create ${describe}:`, await response.text());
        process.exit(1);
    }
    console.log(`Created ${describe}.`);
};

const createRealm = async () => {
    const existing = await fetch(realmUrl, { headers: await authorizationHeader() });
    if (existing.ok) {
        console.log(`Realm '${realmName}' is already there.`);
        return;
    }
    await create(`${keycloakUrl}/admin/realms`, {
        realm: realmName,
        displayName: 'Harbor',
        enabled: true,
        sslRequired: 'none',
        registrationAllowed: false,
        loginWithEmailAllowed: true
    }, `realm '${realmName}'`);
};

const createClient = async () => {
    const existing = await fetch(`${realmUrl}/clients?clientId=${clientId}`, {
        headers: await authorizationHeader()
    });
    const [found] = await existing.json();
    if (found) {
        console.log(`Client '${clientId}' is already there.`);
        return;
    }
    await create(`${realmUrl}/clients`, {
        clientId: clientId,
        name: 'Harbor',
        secret: clientSecret,
        enabled: true,
        protocol: 'openid-connect',
        // Confidential: Harbor holds a secret, so there is no reason to be public.
        publicClient: false,
        standardFlowEnabled: true,
        implicitFlowEnabled: false,
        directAccessGrantsEnabled: false,
        serviceAccountsEnabled: false,
        // The wildcard port is what lets the integration tests run on a random one.
        // 'keycloak' is Spring's registration id from application.properties, not the
        // realm or the client id — Spring builds this callback path from that key.
        redirectUris: ['http://localhost:*/login/oauth2/code/keycloak'],
        webOrigins: ['http://localhost:*'],
        attributes: {
            // Without this, signing out lands on a Keycloak error page instead of Harbor.
            'post.logout.redirect.uris': 'http://localhost:*'
        }
    }, `client '${clientId}'`);
};

const createReader = async () => {
    const existing = await fetch(`${realmUrl}/users?username=${readerUsername}&exact=true`, {
        headers: await authorizationHeader()
    });
    const [found] = await existing.json();
    if (found) {
        console.log(`User '${readerUsername}' is already there, as ${found.id}.`);
        return;
    }
    await create(`${realmUrl}/users`, {
        id: readerId,
        username: readerUsername,
        firstName: 'Harbor',
        lastName: 'Reader',
        email: `${readerUsername}@harbor.invalid`,
        emailVerified: true,
        enabled: true,
        credentials: [{ type: 'password', value: readerPassword, temporary: false }]
    }, `user '${readerUsername}' with the pinned id ${readerId}`);
};

await createRealm();
await createClient();
await createReader();

console.log(`Keycloak is ready: realm '${realmName}', client '${clientId}', user '${readerUsername}'.`);
