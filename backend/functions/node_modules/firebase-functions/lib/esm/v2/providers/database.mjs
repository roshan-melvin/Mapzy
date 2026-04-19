import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initV2Endpoint } from "../../runtime/manifest.mjs";
import { getApp } from "../../common/app.mjs";
import { withInit } from "../../common/onInit.mjs";
import { normalizePath } from "../../common/utilities/path.mjs";
import { DataSnapshot } from "../../common/providers/database.mjs";
import { applyChange } from "../../common/utilities/utils.mjs";
import { wrapTraceContext } from "../trace.mjs";
import { getGlobalOptions, optionsToEndpoint } from "../options.mjs";
import { PathPattern } from "../../common/utilities/path-pattern.mjs";

//#region src/v2/providers/database.ts
var database_exports = /* @__PURE__ */ __export({
	DataSnapshot: () => DataSnapshot,
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
		path = normalizePath(referenceOrOpts);
		instance = "*";
		opts = {};
	} else {
		path = normalizePath(referenceOrOpts.ref);
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
	const snapshot = new DataSnapshot(data, event.ref, getApp(), instance);
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
	const before = new DataSnapshot(event.data.data, event.ref, getApp(), instance);
	const after = new DataSnapshot(applyChange(event.data.data, event.data.delta), event.ref, getApp(), instance);
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
	const baseOpts = optionsToEndpoint(getGlobalOptions());
	const specificOpts = optionsToEndpoint(opts);
	const eventFilters = {};
	const eventFilterPathPatterns = { ref: path.getValue() };
	if (instance.hasWildcards()) {
		eventFilterPathPatterns.instance = instance.getValue();
	} else {
		eventFilters.instance = instance.getValue();
	}
	return {
		...initV2Endpoint(getGlobalOptions(), opts),
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
	const pathPattern = new PathPattern(path);
	const instancePattern = new PathPattern(instance);
	const func = (raw) => {
		const event = raw;
		const instanceUrl = getInstance(event);
		const params = makeParams(event, pathPattern, instancePattern);
		const databaseEvent = makeChangedDatabaseEvent(event, instanceUrl, params);
		return wrapTraceContext(withInit(handler))(databaseEvent);
	};
	func.run = handler;
	func.__endpoint = makeEndpoint(eventType, opts, pathPattern, instancePattern);
	return func;
}
/** @internal */
function onOperation(eventType, referenceOrOpts, handler) {
	const { path, instance, opts } = getOpts(referenceOrOpts);
	const pathPattern = new PathPattern(path);
	const instancePattern = new PathPattern(instance);
	const func = (raw) => {
		const event = raw;
		const instanceUrl = getInstance(event);
		const params = makeParams(event, pathPattern, instancePattern);
		const data = eventType === deletedEventType ? event.data.data : event.data.delta;
		const databaseEvent = makeDatabaseEvent(event, data, instanceUrl, params);
		return wrapTraceContext(withInit(handler))(databaseEvent);
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
export { DataSnapshot, createdEventType, database_exports, deletedEventType, getOpts, makeEndpoint, makeParams, onChangedOperation, onOperation, onValueCreated, onValueDeleted, onValueUpdated, onValueWritten, updatedEventType, writtenEventType };