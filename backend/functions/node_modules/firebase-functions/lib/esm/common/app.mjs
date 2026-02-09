import { firebaseConfig } from "./config.mjs";
import { applicationDefault, deleteApp, getApp as getApp$1, initializeApp } from "firebase-admin/app";

//#region src/common/app.ts
const APP_NAME = "__FIREBASE_FUNCTIONS_SDK__";
let cache;
function getApp() {
	if (typeof cache === "undefined") {
		try {
			cache = getApp$1();
		} catch {
			cache = initializeApp({
				...firebaseConfig(),
				credential: applicationDefault()
			}, APP_NAME);
		}
	}
	return cache;
}
/**
* This function allows the Firebase Emulator Suite to override the FirebaseApp instance
* used by the Firebase Functions SDK. Developers should never call this function for
* other purposes.
* N.B. For clarity for use in testing this name has no mention of emulation, but
* it must be exported from index as app.setEmulatedAdminApp or we break the emulator.
* We can remove this export when:
* A) We complete the new emulator and no longer depend on monkeypatching
* B) We tweak the CLI to look for different APIs to monkeypatch depending on versions.
* @alpha
*/
function setApp(app) {
	if (cache?.name === APP_NAME) {
		void deleteApp(cache);
	}
	cache = app;
}

//#endregion
export { getApp, setApp };