const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_logger_index = require('../../logger/index.js');
const require_runtime_manifest = require('../../runtime/manifest.js');
const require_common_change = require('../../common/change.js');
const require_common_onInit = require('../../common/onInit.js');
const require_common_utilities_path = require('../../common/utilities/path.js');
const require_common_providers_firestore = require('../../common/providers/firestore.js');
const require_v2_trace = require('../trace.js');
require('../core.js');
const require_v2_options = require('../options.js');
const require_common_utilities_path_pattern = require('../../common/utilities/path-pattern.js');

//#region src/v2/providers/firestore.ts
var firestore_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	Change: () => require_common_change.Change,
	createBeforeSnapshot: () => createBeforeSnapshot,
	createSnapshot: () => createSnapshot,
	createdEventType: () => createdEventType,
	createdEventWithAuthContextType: () => createdEventWithAuthContextType,
	deletedEventType: () => deletedEventType,
	deletedEventWithAuthContextType: () => deletedEventWithAuthContextType,
	getOpts: () => getOpts,
	makeChangedFirestoreEvent: () => makeChangedFirestoreEvent,
	makeEndpoint: () => makeEndpoint,
	makeFirestoreEvent: () => makeFirestoreEvent,
	makeParams: () => makeParams,
	onChangedOperation: () => onChangedOperation,
	onDocumentCreated: () => onDocumentCreated,
	onDocumentCreatedWithAuthContext: () => onDocumentCreatedWithAuthContext,
	onDocumentDeleted: () => onDocumentDeleted,
	onDocumentDeletedWithAuthContext: () => onDocumentDeletedWithAuthContext,
	onDocumentUpdated: () => onDocumentUpdated,
	onDocumentUpdatedWithAuthContext: () => onDocumentUpdatedWithAuthContext,
	onDocumentWritten: () => onDocumentWritten,
	onDocumentWrittenWithAuthContext: () => onDocumentWrittenWithAuthContext,
	onOperation: () => onOperation,
	updatedEventType: () => updatedEventType,
	updatedEventWithAuthContextType: () => updatedEventWithAuthContextType,
	writtenEventType: () => writtenEventType,
	writtenEventWithAuthContextType: () => writtenEventWithAuthContextType
});
/** @internal */
const writtenEventType = "google.cloud.firestore.document.v1.written";
/** @internal */
const createdEventType = "google.cloud.firestore.document.v1.created";
/** @internal */
const updatedEventType = "google.cloud.firestore.document.v1.updated";
/** @internal */
const deletedEventType = "google.cloud.firestore.document.v1.deleted";
/** @internal */
const writtenEventWithAuthContextType = "google.cloud.firestore.document.v1.written.withAuthContext";
/** @internal */
const createdEventWithAuthContextType = "google.cloud.firestore.document.v1.created.withAuthContext";
/** @internal */
const updatedEventWithAuthContextType = "google.cloud.firestore.document.v1.updated.withAuthContext";
/** @internal */
const deletedEventWithAuthContextType = "google.cloud.firestore.document.v1.deleted.withAuthContext";
/**
* Event handler that triggers when a document is created, updated, or deleted in Firestore.
*
* @param documentOrOpts - Options or a string document path.
* @param handler - Event handler which is run every time a Firestore create, update, or delete occurs.
*/
function onDocumentWritten(documentOrOpts, handler) {
	return onChangedOperation(writtenEventType, documentOrOpts, handler);
}
/**
* Event handler that triggers when a document is created, updated, or deleted in Firestore.
* This trigger also provides the authentication context of the principal who triggered the event.
*
* @param opts - Options or a string document path.
* @param handler - Event handler which is run every time a Firestore create, update, or delete occurs.
*/
function onDocumentWrittenWithAuthContext(documentOrOpts, handler) {
	return onChangedOperation(writtenEventWithAuthContextType, documentOrOpts, handler);
}
/**
* Event handler that triggers when a document is created in Firestore.
*
* @param documentOrOpts - Options or a string document path.
* @param handler - Event handler which is run every time a Firestore create occurs.
*/
function onDocumentCreated(documentOrOpts, handler) {
	return onOperation(createdEventType, documentOrOpts, handler);
}
/**
* Event handler that triggers when a document is created in Firestore.
*
* @param documentOrOpts - Options or a string document path.
* @param handler - Event handler which is run every time a Firestore create occurs.
*/
function onDocumentCreatedWithAuthContext(documentOrOpts, handler) {
	return onOperation(createdEventWithAuthContextType, documentOrOpts, handler);
}
/**
* Event handler that triggers when a document is updated in Firestore.
*
* @param documentOrOpts - Options or a string document path.
* @param handler - Event handler which is run every time a Firestore update occurs.
*/
function onDocumentUpdated(documentOrOpts, handler) {
	return onChangedOperation(updatedEventType, documentOrOpts, handler);
}
/**
* Event handler that triggers when a document is updated in Firestore.
*
* @param documentOrOpts - Options or a string document path.
* @param handler - Event handler which is run every time a Firestore update occurs.
*/
function onDocumentUpdatedWithAuthContext(documentOrOpts, handler) {
	return onChangedOperation(updatedEventWithAuthContextType, documentOrOpts, handler);
}
/**
* Event handler that triggers when a document is deleted in Firestore.
*
* @param documentOrOpts - Options or a string document path.
* @param handler - Event handler which is run every time a Firestore delete occurs.
*/
function onDocumentDeleted(documentOrOpts, handler) {
	return onOperation(deletedEventType, documentOrOpts, handler);
}
/**
* Event handler that triggers when a document is deleted in Firestore.
*
* @param documentOrOpts - Options or a string document path.
* @param handler - Event handler which is run every time a Firestore delete occurs.
*/
function onDocumentDeletedWithAuthContext(documentOrOpts, handler) {
	return onOperation(deletedEventWithAuthContextType, documentOrOpts, handler);
}
/** @internal */
function getOpts(documentOrOpts) {
	let document;
	let database;
	let namespace;
	let opts;
	if (typeof documentOrOpts === "string") {
		document = require_common_utilities_path.normalizePath(documentOrOpts);
		database = "(default)";
		namespace = "(default)";
		opts = {};
	} else {
		document = typeof documentOrOpts.document === "string" ? require_common_utilities_path.normalizePath(documentOrOpts.document) : documentOrOpts.document;
		database = documentOrOpts.database || "(default)";
		namespace = documentOrOpts.namespace || "(default)";
		opts = { ...documentOrOpts };
		delete opts.document;
		delete opts.database;
		delete opts.namespace;
	}
	return {
		document,
		database,
		namespace,
		opts
	};
}
/** @hidden */
function getPath(event) {
	return `projects/${event.project}/databases/${event.database}/documents/${event.document}`;
}
/** @internal */
function createSnapshot(event) {
	if (event.datacontenttype?.includes("application/protobuf") || Buffer.isBuffer(event.data)) {
		return require_common_providers_firestore.createSnapshotFromProtobuf(event.data, getPath(event), event.database);
	} else if (event.datacontenttype?.includes("application/json")) {
		return require_common_providers_firestore.createSnapshotFromJson(event.data, event.source, event.data.value?.createTime, event.data.value?.updateTime, event.database);
	} else {
		require_logger_index.error(`Cannot determine payload type, datacontenttype is ${event.datacontenttype}, failing out.`);
		throw Error("Error: Cannot parse event payload.");
	}
}
/** @internal */
function createBeforeSnapshot(event) {
	if (event.datacontenttype?.includes("application/protobuf") || Buffer.isBuffer(event.data)) {
		return require_common_providers_firestore.createBeforeSnapshotFromProtobuf(event.data, getPath(event), event.database);
	} else if (event.datacontenttype?.includes("application/json")) {
		return require_common_providers_firestore.createBeforeSnapshotFromJson(event.data, event.source, event.data.oldValue?.createTime, event.data.oldValue?.updateTime, event.database);
	} else {
		require_logger_index.error(`Cannot determine payload type, datacontenttype is ${event.datacontenttype}, failing out.`);
		throw Error("Error: Cannot parse event payload.");
	}
}
/** @internal */
function makeParams(document, documentPattern) {
	return { ...documentPattern.extractMatches(document) };
}
/** @internal */
function makeFirestoreEvent(eventType, event, params) {
	const data = event.data ? eventType === createdEventType || eventType === createdEventWithAuthContextType ? createSnapshot(event) : createBeforeSnapshot(event) : undefined;
	const firestoreEvent = {
		...event,
		params,
		data
	};
	delete firestoreEvent.datacontenttype;
	delete firestoreEvent.dataschema;
	if ("authtype" in event) {
		const eventWithAuth = {
			...firestoreEvent,
			authType: event.authtype,
			authId: event.authid
		};
		delete eventWithAuth.authtype;
		delete eventWithAuth.authid;
		return eventWithAuth;
	}
	return firestoreEvent;
}
/** @internal */
function makeChangedFirestoreEvent(event, params) {
	const data = event.data ? require_common_change.Change.fromObjects(createBeforeSnapshot(event), createSnapshot(event)) : undefined;
	const firestoreEvent = {
		...event,
		params,
		data
	};
	delete firestoreEvent.datacontenttype;
	delete firestoreEvent.dataschema;
	if ("authtype" in event) {
		const eventWithAuth = {
			...firestoreEvent,
			authType: event.authtype,
			authId: event.authid
		};
		delete eventWithAuth.authtype;
		delete eventWithAuth.authid;
		return eventWithAuth;
	}
	return firestoreEvent;
}
/** @internal */
function makeEndpoint(eventType, opts, document, database, namespace) {
	const baseOpts = require_v2_options.optionsToEndpoint(require_v2_options.getGlobalOptions());
	const specificOpts = require_v2_options.optionsToEndpoint(opts);
	const eventFilters = {
		database,
		namespace
	};
	const eventFilterPathPatterns = {};
	const maybePattern = typeof document === "string" ? new require_common_utilities_path_pattern.PathPattern(document).hasWildcards() : true;
	if (maybePattern) {
		eventFilterPathPatterns.document = document;
	} else {
		eventFilters.document = document;
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
function onOperation(eventType, documentOrOpts, handler) {
	const { document, database, namespace, opts } = getOpts(documentOrOpts);
	const func = (raw) => {
		const event = raw;
		const documentPattern = new require_common_utilities_path_pattern.PathPattern(typeof document === "string" ? document : document.value());
		const params = makeParams(event.document, documentPattern);
		const firestoreEvent = makeFirestoreEvent(eventType, event, params);
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler))(firestoreEvent);
	};
	func.run = handler;
	func.__endpoint = makeEndpoint(eventType, opts, document, database, namespace);
	return func;
}
/** @internal */
function onChangedOperation(eventType, documentOrOpts, handler) {
	const { document, database, namespace, opts } = getOpts(documentOrOpts);
	const func = (raw) => {
		const event = raw;
		const documentPattern = new require_common_utilities_path_pattern.PathPattern(typeof document === "string" ? document : document.value());
		const params = makeParams(event.document, documentPattern);
		const firestoreEvent = makeChangedFirestoreEvent(event, params);
		return require_v2_trace.wrapTraceContext(require_common_onInit.withInit(handler))(firestoreEvent);
	};
	func.run = handler;
	func.__endpoint = makeEndpoint(eventType, opts, document, database, namespace);
	return func;
}

//#endregion
exports.Change = require_common_change.Change;
exports.createBeforeSnapshot = createBeforeSnapshot;
exports.createSnapshot = createSnapshot;
exports.createdEventType = createdEventType;
exports.createdEventWithAuthContextType = createdEventWithAuthContextType;
exports.deletedEventType = deletedEventType;
exports.deletedEventWithAuthContextType = deletedEventWithAuthContextType;
Object.defineProperty(exports, 'firestore_exports', {
  enumerable: true,
  get: function () {
    return firestore_exports;
  }
});
exports.getOpts = getOpts;
exports.makeChangedFirestoreEvent = makeChangedFirestoreEvent;
exports.makeEndpoint = makeEndpoint;
exports.makeFirestoreEvent = makeFirestoreEvent;
exports.makeParams = makeParams;
exports.onChangedOperation = onChangedOperation;
exports.onDocumentCreated = onDocumentCreated;
exports.onDocumentCreatedWithAuthContext = onDocumentCreatedWithAuthContext;
exports.onDocumentDeleted = onDocumentDeleted;
exports.onDocumentDeletedWithAuthContext = onDocumentDeletedWithAuthContext;
exports.onDocumentUpdated = onDocumentUpdated;
exports.onDocumentUpdatedWithAuthContext = onDocumentUpdatedWithAuthContext;
exports.onDocumentWritten = onDocumentWritten;
exports.onDocumentWrittenWithAuthContext = onDocumentWrittenWithAuthContext;
exports.onOperation = onOperation;
exports.updatedEventType = updatedEventType;
exports.updatedEventWithAuthContextType = updatedEventWithAuthContextType;
exports.writtenEventType = writtenEventType;
exports.writtenEventWithAuthContextType = writtenEventWithAuthContextType;