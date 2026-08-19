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
 * client will redirect anywhere at all. A deployment configures its own realm — very
 * likely one it already runs for other applications — and shares nothing with this file.
 */

const keycloakUrl = process.env.KC_URL;
const adminUsername = process.env.KC_ADMIN_USERNAME;
const adminPassword = process.env.KC_ADMIN_PASSWORD;

const realmName = 'harbor';
const clientId = 'harbor';
const clientSecret = 'harbor-dev-secret';

/*
 * No id here, because the admin API will not take one: it assigns the user's id itself
 * and ignores anything supplied on create. Keycloak's own realm *import* does honour a
 * pinned id, which is the one thing the export approach could do that this cannot.
 *
 * The consequence worth knowing: that id is the `sub` in every token and so the owner of
 * every row this reader writes, so throwing the Keycloak container away on its own
 * orphans the development library. `./run.sh env reset` clears both together.
 */
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
        // Wide open, because a realm's redirect URIs are not Harbor's business to get
        // right — a deployment adds Harbor's exact callback to a realm it already runs,
        // very likely alongside other applications. This one exists so a laptop and a test
        // suite can log in on whatever port they happen to be using.
        redirectUris: ['*'],
        webOrigins: ['*'],
        attributes: {
            // Without this, signing out lands on a Keycloak error page instead of Harbor.
            'post.logout.redirect.uris': '*'
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
        username: readerUsername,
        // A complete profile, deliberately. Keycloak's VERIFY_PROFILE action fires on
        // login for a user missing a required attribute — firstName and lastName are
        // required by the default user profile — and parks the browser on a "complete
        // your account" form that reads like a login which did not take.
        firstName: 'Harbor',
        lastName: 'Reader',
        email: `${readerUsername}@harbor.invalid`,
        emailVerified: true,
        enabled: true,
        requiredActions: [],
        credentials: [{ type: 'password', value: readerPassword, temporary: false }]
    }, `user '${readerUsername}'`);
};

await createRealm();
await createClient();
await createReader();

console.log(`Keycloak is ready: realm '${realmName}', client '${clientId}', user '${readerUsername}'.`);
