const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_common_config = require('../../common/config.js');
const require_common_app = require('../../common/app.js');
const require_v1_cloud_functions = require('../cloud-functions.js');
const require_common_utilities_path = require('../../common/utilities/path.js');
const require_common_providers_database = require('../../common/providers/database.js');
const require_common_utilities_utils = require('../../common/utilities/utils.js');

//#region src/v1/providers/database.ts
var database_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	DataSnapshot: () => require_common_providers_database.DataSnapshot,
	InstanceBuilder: () => InstanceBuilder,
	RefBuilder: () => RefBuilder,
	_instanceWithOptions: () => _instanceWithOptions,
	_refWithOptions: () => _refWithOptions,
	extractInstanceAndPath: () => extractInstanceAndPath,
	instance: () => instance,
	provider: () => provider,
	ref: () => ref,
	service: () => service
});
/** @internal */
const provider = "google.firebase.database";
/** @internal */
const service = "firebaseio.com";
const databaseURLRegex = new RegExp("^https://([^.]+).");
const emulatorDatabaseURLRegex = new RegExp("^http://.*ns=([^&]+)");
/**
* Registers a function that triggers on events from a specific
* Firebase Realtime Database instance.
*
* @remarks
* Use this method together with `ref` to specify the instance on which to
* watch for database events. For example: `firebase.database.instance('my-app-db-2').ref('/foo/bar')`
*
* Note that `functions.database.ref` used without `instance` watches the
* *default* instance for events.
*
* @param instance The instance name of the database instance
*   to watch for write events.
* @returns Firebase Realtime Database instance builder interface.
*/
function instance(instance$1) {
	return _instanceWithOptions(instance$1, {});
}
/**
* Registers a function that triggers on Firebase Realtime Database write
* events.
*
* @remarks
* This method behaves very similarly to the method of the same name in the
* client and Admin Firebase SDKs. Any change to the Database that affects the
* data at or below the provided `path` will fire an event in Cloud Functions.
*
* There are three important differences between listening to a Realtime
* Database event in Cloud Functions and using the Realtime Database in the
* client and Admin SDKs:
*
* 1. Cloud Functions allows wildcards in the `path` name. Any `path` component
*    in curly brackets (`{}`) is a wildcard that matches all strings. The value
*    that matched a certain invocation of a Cloud Function is returned as part
*    of the [`EventContext.params`](cloud_functions_eventcontext.html#params object. For
*    example, `ref("messages/{messageId}")` matches changes at
*    `/messages/message1` or `/messages/message2`, resulting in
*    `event.params.messageId` being set to `"message1"` or `"message2"`,
*    respectively.
*
* 2. Cloud Functions do not fire an event for data that already existed before
*    the Cloud Function was deployed.
*
* 3. Cloud Function events have access to more information, including a
*    snapshot of the previous event data and information about the user who
*    triggered the Cloud Function.
*
* @param path The path within the Database to watch for write events.
* @returns Firebase Realtime Database builder interface.
*/
function ref(path) {
	return _refWithOptions(path, {});
}
/** @internal */
function _instanceWithOptions(instance$1, options) {
	return new InstanceBuilder(instance$1, options);
}
/**
* The Firebase Realtime Database instance builder interface.
*
* Access via [`database.instance()`](providers_database_.html#instance).
*/
var InstanceBuilder = class {
	constructor(instance$1, options) {
		this.instance = instance$1;
		this.options = options;
	}
	/**
	* @returns Firebase Realtime Database reference builder interface.
	*/
	ref(path) {
		const normalized = require_common_utilities_path.normalizePath(path);
		return new RefBuilder(() => `projects/_/instances/${this.instance}/refs/${normalized}`, this.options);
	}
};
/** @internal */
function _refWithOptions(path, options) {
	const resourceGetter = () => {
		const normalized = require_common_utilities_path.normalizePath(path);
		const databaseURL = require_common_config.firebaseConfig().databaseURL;
		if (!databaseURL) {
			throw new Error("Missing expected firebase config value databaseURL, " + "config is actually" + JSON.stringify(require_common_config.firebaseConfig()) + "\n If you are unit testing, please set process.env.FIREBASE_CONFIG");
		}
		let instance$1;
		const prodMatch = databaseURL.match(databaseURLRegex);
		if (prodMatch) {
			instance$1 = prodMatch[1];
		} else {
			const emulatorMatch = databaseURL.match(emulatorDatabaseURLRegex);
			if (emulatorMatch) {
				instance$1 = emulatorMatch[1];
			}
		}
		if (!instance$1) {
			throw new Error("Invalid value for config firebase.databaseURL: " + databaseURL);
		}
		return `projects/_/instances/${instance$1}/refs/${normalized}`;
	};
	return new RefBuilder(resourceGetter, options);
}
/**
* The Firebase Realtime Database reference builder interface.
*
* Access via [`functions.database.ref()`](functions.database#.ref).
*/
var RefBuilder = class {
	constructor(triggerResource, options) {
		this.triggerResource = triggerResource;
		this.options = options;
		this.changeConstructor = (raw) => {
			const [dbInstance, path] = extractInstanceAndPath(raw.context.resource.name, raw.context.domain);
			const before = new require_common_providers_database.DataSnapshot(raw.data.data, path, require_common_app.getApp(), dbInstance);
			const after = new require_common_providers_database.DataSnapshot(require_common_utilities_utils.applyChange(raw.data.data, raw.data.delta), path, require_common_app.getApp(), dbInstance);
			return {
				before,
				after
			};
		};
	}
	/**
	* Event handler that fires every time a Firebase Realtime Database write
	* of any kind (creation, update, or delete) occurs.
	*
	* @param handler Event handler that runs every time a Firebase Realtime Database
	*   write occurs.
	* @returns A function that you can export and deploy.
	*/
	onWrite(handler) {
		return this.onOperation(handler, "ref.write", this.changeConstructor);
	}
	/**
	* Event handler that fires every time data is updated in
	* Firebase Realtime Database.
	*
	* @param handler Event handler which is run every time a Firebase Realtime Database
	*   write occurs.
	* @returns A function which you can export and deploy.
	*/
	onUpdate(handler) {
		return this.onOperation(handler, "ref.update", this.changeConstructor);
	}
	/**
	* Event handler that fires every time new data is created in
	* Firebase Realtime Database.
	*
	* @param handler Event handler that runs every time new data is created in
	*   Firebase Realtime Database.
	* @returns A function that you can export and deploy.
	*/
	onCreate(handler) {
		const dataConstructor = (raw) => {
			const [dbInstance, path] = extractInstanceAndPath(raw.context.resource.name, raw.context.domain);
			return new require_common_providers_database.DataSnapshot(raw.data.delta, path, require_common_app.getApp(), dbInstance);
		};
		return this.onOperation(handler, "ref.create", dataConstructor);
	}
	/**
	* Event handler that fires every time data is deleted from
	* Firebase Realtime Database.
	*
	* @param handler Event handler that runs every time data is deleted from
	*   Firebase Realtime Database.
	* @returns A function that you can export and deploy.
	*/
	onDelete(handler) {
		const dataConstructor = (raw) => {
			const [dbInstance, path] = extractInstanceAndPath(raw.context.resource.name, raw.context.domain);
			return new require_common_providers_database.DataSnapshot(raw.data.data, path, require_common_app.getApp(), dbInstance);
		};
		return this.onOperation(handler, "ref.delete", dataConstructor);
	}
	onOperation(handler, eventType, dataConstructor) {
		return require_v1_cloud_functions.makeCloudFunction({
			handler,
			provider,
			service,
			eventType,
			legacyEventType: `providers/${provider}/eventTypes/${eventType}`,
			triggerResource: this.triggerResource,
			dataConstructor,
			options: this.options
		});
	}
};
const resourceRegex = /^projects\/([^/]+)\/instances\/([a-zA-Z0-9-]+)\/refs(\/.+)?/;
/**
* Utility function to extract database reference from resource string
*
* @param optional database domain override for the original of the source database.
*    It defaults to `firebaseio.com`.
*    Multi-region RTDB will be served from different domains.
*    Since region is not part of the resource name, it is provided through context.
*
* @internal
*/
function extractInstanceAndPath(resource, domain = "firebaseio.com") {
	const match = resource.match(new RegExp(resourceRegex));
	if (!match) {
		throw new Error(`Unexpected resource string for Firebase Realtime Database event: ${resource}. ` + "Expected string in the format of \"projects/_/instances/{firebaseioSubdomain}/refs/{ref=**}\"");
	}
	const [, project, dbInstanceName, path] = match;
	if (project !== "_") {
		throw new Error(`Expect project to be '_' in a Firebase Realtime Database event`);
	}
	const emuHost = process.env.FIREBASE_DATABASE_EMULATOR_HOST;
	if (emuHost) {
		const dbInstance = `http://${emuHost}/?ns=${dbInstanceName}`;
		return [dbInstance, path];
	} else {
		const dbInstance = "https://" + dbInstanceName + "." + domain;
		return [dbInstance, path];
	}
}

//#endregion
exports.DataSnapshot = require_common_providers_database.DataSnapshot;
exports.InstanceBuilder = InstanceBuilder;
exports.RefBuilder = RefBuilder;
exports._instanceWithOptions = _instanceWithOptions;
exports._refWithOptions = _refWithOptions;
Object.defineProperty(exports, 'database_exports', {
  enumerable: true,
  get: function () {
    return database_exports;
  }
});
exports.extractInstanceAndPath = extractInstanceAndPath;
exports.instance = instance;
exports.provider = provider;
exports.ref = ref;
exports.service = service;