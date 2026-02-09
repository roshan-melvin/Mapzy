import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { Change } from "../../common/change.mjs";
import { makeCloudFunction } from "../cloud-functions.mjs";
import { createBeforeSnapshotFromJson, createSnapshotFromJson } from "../../common/providers/firestore.mjs";
import { posix } from "path";

//#region src/v1/providers/firestore.ts
var firestore_exports = /* @__PURE__ */ __export({
	DatabaseBuilder: () => DatabaseBuilder,
	DocumentBuilder: () => DocumentBuilder,
	NamespaceBuilder: () => NamespaceBuilder,
	_databaseWithOptions: () => _databaseWithOptions,
	_documentWithOptions: () => _documentWithOptions,
	_namespaceWithOptions: () => _namespaceWithOptions,
	beforeSnapshotConstructor: () => beforeSnapshotConstructor,
	database: () => database,
	defaultDatabase: () => defaultDatabase,
	document: () => document,
	namespace: () => namespace,
	provider: () => provider,
	service: () => service,
	snapshotConstructor: () => snapshotConstructor
});
/** @internal */
const provider = "google.firestore";
/** @internal */
const service = "firestore.googleapis.com";
/** @internal */
const defaultDatabase = "(default)";
/**
* Select the Firestore document to listen to for events.
* @param path Full database path to listen to. This includes the name of
* the collection that the document is a part of. For example, if the
* collection is named "users" and the document is named "Ada", then the
* path is "/users/Ada".
*/
function document(path) {
	return _documentWithOptions(path, {});
}
function namespace(namespace$1) {
	return _namespaceWithOptions(namespace$1, {});
}
function database(database$1) {
	return _databaseWithOptions(database$1, {});
}
/** @internal */
function _databaseWithOptions(database$1 = defaultDatabase, options) {
	return new DatabaseBuilder(database$1, options);
}
/** @internal */
function _namespaceWithOptions(namespace$1, options) {
	return _databaseWithOptions(defaultDatabase, options).namespace(namespace$1);
}
/** @internal */
function _documentWithOptions(path, options) {
	return _databaseWithOptions(defaultDatabase, options).document(path);
}
var DatabaseBuilder = class {
	constructor(database$1, options) {
		this.database = database$1;
		this.options = options;
	}
	namespace(namespace$1) {
		return new NamespaceBuilder(this.database, this.options, namespace$1);
	}
	document(path) {
		return new NamespaceBuilder(this.database, this.options).document(path);
	}
};
var NamespaceBuilder = class {
	constructor(database$1, options, namespace$1) {
		this.database = database$1;
		this.options = options;
		this.namespace = namespace$1;
	}
	document(path) {
		return new DocumentBuilder(() => {
			if (!process.env.GCLOUD_PROJECT) {
				throw new Error("process.env.GCLOUD_PROJECT is not set.");
			}
			const database$1 = posix.join("projects", process.env.GCLOUD_PROJECT, "databases", this.database);
			return posix.join(database$1, this.namespace ? `documents@${this.namespace}` : "documents", path);
		}, this.options);
	}
};
function snapshotConstructor(event) {
	return createSnapshotFromJson(event.data, event.context.resource.name, event?.data?.value?.readTime, event?.data?.value?.updateTime);
}
function beforeSnapshotConstructor(event) {
	return createBeforeSnapshotFromJson(event.data, event.context.resource.name, event?.data?.oldValue?.readTime, undefined);
}
function changeConstructor(raw) {
	return Change.fromObjects(beforeSnapshotConstructor(raw), snapshotConstructor(raw));
}
var DocumentBuilder = class {
	constructor(triggerResource, options) {
		this.triggerResource = triggerResource;
		this.options = options;
	}
	/** Respond to all document writes (creates, updates, or deletes). */
	onWrite(handler) {
		return this.onOperation(handler, "document.write", changeConstructor);
	}
	/** Respond only to document updates. */
	onUpdate(handler) {
		return this.onOperation(handler, "document.update", changeConstructor);
	}
	/** Respond only to document creations. */
	onCreate(handler) {
		return this.onOperation(handler, "document.create", snapshotConstructor);
	}
	/** Respond only to document deletions. */
	onDelete(handler) {
		return this.onOperation(handler, "document.delete", beforeSnapshotConstructor);
	}
	onOperation(handler, eventType, dataConstructor) {
		return makeCloudFunction({
			handler,
			provider,
			eventType,
			service,
			triggerResource: this.triggerResource,
			legacyEventType: `providers/cloud.firestore/eventTypes/${eventType}`,
			dataConstructor,
			options: this.options
		});
	}
};

//#endregion
export { DatabaseBuilder, DocumentBuilder, NamespaceBuilder, _databaseWithOptions, _documentWithOptions, _namespaceWithOptions, beforeSnapshotConstructor, database, defaultDatabase, document, firestore_exports, namespace, provider, service, snapshotConstructor };