import { firebaseConfig } from "../common/config.mjs";

//#region src/v1/config.ts
/**
* @deprecated `functions.config()` has been removed in firebase-functions v7.
* Migrate to environment parameters using the `params` module immediately.
* Migration guide: https://firebase.google.com/docs/functions/config-env#migrate-config
*/
const config = (() => {
	throw new Error("functions.config() has been removed in firebase-functions v7. " + "Migrate to environment parameters using the params module. " + "Migration guide: https://firebase.google.com/docs/functions/config-env#migrate-config");
});

//#endregion
export { config, firebaseConfig };