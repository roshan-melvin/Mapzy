const require_rolldown_runtime = require('../_virtual/rolldown_runtime.js');
const require_common_config = require('./config.js');
let firebase_admin_app = require("firebase-admin/app");
firebase_admin_app = require_rolldown_runtime.__toESM(firebase_admin_app);

//#region src/common/app.ts
const APP_NAME = "__FIREBASE_FUNCTIONS_SDK__";
let cache;
function getApp() {
	if (typeof cache === "undefined") {
		try {
			cache = (0, firebase_admin_app.getApp)();
		} catch {
			cache = (0, firebase_admin_app.initializeApp)({
				...require_common_config.firebaseConfig(),
				credential: (0, firebase_admin_app.applicationDefault)()
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
		void (0, firebase_admin_app.deleteApp)(cache);
	}
	cache = app;
}

//#endregion
exports.getApp = getApp;
exports.setApp = setApp;