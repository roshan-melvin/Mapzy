const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_app = require('../../common/app.js');
const require_common_onInit = require('../../common/onInit.js');
const require_common_utilities_path = require('../../common/utilities/path.js');
const require_common_providers_database = require('../../common/providers/database.js');
const require_common_utilities_utils = require('../../common/utilities/utils.js');
const require_v2_trace = require('../trace.js');
const require_v2_options = require('../options.js');
const require_common_utilities_path_pattern = require('../../common/utilities/path-pattern.js');

//#region src/v2/providers/database.ts
var database_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	DataSnapshot: () => require_common_providers_database.DataSnapshot,
	createdEventType: () => createdEventType,
	deletedEventType: () => deletedEventType,
	getOpts: () => getOpts,
	makeEndpoint: () => makeEndpoint,
	makeParams: () => makeParams,
	onChangedOperation: () => onChangedOperation,
	onOperation: () => onOperation,
	onValueCreated: () => onValueCreated,
	onValueDeleted: () => onValueDeleted,
	onValueUpdated: () => onValueUpdated,
	onValueWritten: () => onValueWritten,
	updatedEventType: () => updatedEventType,
	writtenEventType: () => writtenEventType
});
/** @internal */
const writtenEventType = "google.firebase.database.ref.v1.written";
/** @internal */
const createdEventType = "google.firebase.database.ref.v1.created";
/** @internal */
const updatedEventType = "google.firebase.database.ref.v1.updated";
/** @internal */
const deletedEventType = "google.firebase.database.ref.v1.deleted";
/**
* Event handler which triggers when data is created, updated, or deleted in Realtime Database.
*
* @param referenceOrOpts - Options or a string reference.
* @param handler - Event handler which is run every time a Realtime Database create, update, or delete occurs.
*/
function onValueWritten(referenceOrOpts, handler) {
	return onChangedOperation(writtenEventType, referenceOrOpts, handler);
}
/**
* Event handler which triggers when data is created in Realtime Database.
*
* @param referenceOrOpts - Options or a string reference.
* @param handler - Event handler which is run every time a Realtime Database create occurs.
*/
function onValueCreated(referenceOrOpts, handler) {
	return onOperation(createdEventType, referenceOrOpts, handler);
}
/**
* Event handler which triggers when data is updated in Realtime Database.
*
* @param referenceOrOpts - Options or a string reference.
* @param handler - Event handler which is run every time a Realtime Database update occurs.
*/
function onValueUpdated(referenceOrOpts, handler) {
	return onChangedOperation(updatedEventType, referenceOrOpts, handler);
}
/**
* Event handler which triggers when data is deleted in Realtime Database.
*
* @param referenceOrOpts - Options or a string reference.
* @param handler - Event handler which is run every time a Realtime Database deletion occurs.
*/
function onValueDeleted(referenceOrOpts, handler) {
	return onOperation(deletedEventType, referenceOrOpts, handler);
}
/** @internal */
function getOpts(referenceOrOpts) {
	let path;
	let instance;
	let opts;
	if (typeof referenceOrOpts === "string") {
		path = require_common_utilities_path.normalizePath(referenceOrOpts);
		instance = "*";
		opts = {};
	} else {
		path = require_common_utilities_path.normalizePath(referenceOrOpts.ref);
		instance = referenceOrOpts.instance || "*";
		opts = { ...referenceOrOpts };
		delete opts.ref;
		delete opts.instance;
	}
	return {
		path,
		instance,
		opts
	};
}
/** @internal */
function makeParams(event, path, instance) {
	return {
		...path.extractMatches(event.ref),
		...instance.extractMatches(event.instance)
	};
}
/** @hidden */
function makeDatabaseEvent(event, data, instance, params) {
	const snapshot = new require_common_providers_database.DataSnapshot(data, event.ref, require_common_app.getApp(), instance);
	const databaseEvent = {
		...event,
		firebaseDatabaseHost: event.firebasedatabasehost,
		data: snapshot,
		params
	};
	delete databaseEvent.firebasedatabasehost;
	return databaseEvent;
}
/** @hidden */
function makeChangedDatabaseEvent(event, instance, params) {
	const before = new require_common_providers_database.DataSnapshot(event.data.data, event.ref, require_common_app.getApp(), instance);
	const after = new require_common_providers_database.DataSnapshot(require_common_utilities_utils.applyChange(event.data.data, event.data.delta), event.ref, require_common_app.getApp(), instance);
	const databaseEvent = {
		...event,
		firebaseDatabaseHost: event.firebasedatabasehost,
		data: {
			before,
			after
		},
		params
	};
	delete databaseEvent.firebasedatabasehost;
	return databaseEvent;
}
/** @internal */
function makeEndpoint(eventType, opts, path, instance) {
	const baseOpts = require_v2_options.optionsToEndpoint(require_v2_options.getGlobalOptions());
	const specificOpts = require_v2_options.optionsToEndpoint(opts);
	const eventFilters = {};
	const eventFilterPathPatterns = { ref: path.getValue() };
	if (instance.hasWildcards()) {
		eventFilterPathPatterns.instance = instance.getValue();
	} else {
		eventFilters.instance = instance.getValue();
	}
	return {
		...require_runtime_manifest.initV2Endpoint(require_v2_options.getGlobalOptions(), opts),
		platform: "gcfv2",
		...baseOpts,
		...specificOpts,
		labels: {
			...baseOpts?.labels,
			...specificOpts?.labels
		},
		eventTrigger: {
			eventType,
			eventFilters,
			eventFilterPathPatterns,
			retry: opts.retry ?? false
		}
	};
}
/** @internal */
function onChangedOperation(eventType, referenceOrOpts, handler) {
	const { path, instance, opts } = getOpts(referenceOrOpts);
	const pathPattern = new require_common_utilities_path_pattern.PathPattern(path);
	const instancePattern = new require_common_utilities_path_pattern.PathPattern(instance);
	const func = (raw) => {
		const event = raw;
		const instanceUrl = getInstance(event);
		const params = makeParams(event, pathPattern, instancePattern);
		const databaseEvent = makeChangedDatabaseEvent(event, instanceUrl, params);
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler))(databaseEvent);
	};
	func.run = handler;
	func.__endpoint = makeEndpoint(eventType, opts, pathPattern, instancePattern);
	return func;
}
/** @internal */
function onOperation(eventType, referenceOrOpts, handler) {
	const { path, instance, opts } = getOpts(referenceOrOpts);
	const pathPattern = new require_common_utilities_path_pattern.PathPattern(path);
	const instancePattern = new require_common_utilities_path_pattern.PathPattern(instance);
	const func = (raw) => {
		const event = raw;
		const instanceUrl = getInstance(event);
		const params = makeParams(event, pathPattern, instancePattern);
		const data = eventType === deletedEventType ? event.data.data : event.data.delta;
		const databaseEvent = makeDatabaseEvent(event, data, instanceUrl, params);
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler))(databaseEvent);
	};
	func.run = handler;
	func.__endpoint = makeEndpoint(eventType, opts, pathPattern, instancePattern);
	return func;
}
function getInstance(event) {
	const emuHost = process.env.FIREBASE_DATABASE_EMULATOR_HOST;
	return emuHost ? `http://${emuHost}/?ns=${event.instance}` : `https://${event.instance}.${event.firebasedatabasehost}`;
}

//#endregion
exports.DataSnapshot = require_common_providers_database.DataSnapshot;
exports.createdEventType = createdEventType;
Object.defineProperty(exports, 'database_exports', {
  enumerable: true,
  get: function () {
    return database_exports;
  }
});
exports.deletedEventType = deletedEventType;
exports.getOpts = getOpts;
exports.makeEndpoint = makeEndpoint;
exports.makeParams = makeParams;
exports.onChangedOperation = onChangedOperation;
exports.onOperation = onOperation;
exports.onValueCreated = onValueCreated;
exports.onValueDeleted = onValueDeleted;
exports.onValueUpdated = onValueUpdated;
exports.onValueWritten = onValueWritten;
exports.updatedEventType = updatedEventType;
exports.writtenEventType = writtenEventType;