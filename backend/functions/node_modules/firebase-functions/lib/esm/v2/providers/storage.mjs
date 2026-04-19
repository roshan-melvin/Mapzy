import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { initV2Endpoint } from "../../runtime/manifest.mjs";
import { firebaseConfig } from "../../common/config.mjs";
import { copyIfPresent } from "../../common/encoding.mjs";
import { withInit } from "../../common/onInit.mjs";
import { wrapTraceContext } from "../trace.mjs";
import { getGlobalOptions, optionsToEndpoint, optionsToTriggerAnnotations } from "../options.mjs";

//#region src/v2/providers/storage.ts
var storage_exports = /* @__PURE__ */ __export({
	archivedEvent: () => archivedEvent,
	deletedEvent: () => deletedEvent,
	finalizedEvent: () => finalizedEvent,
	getOptsAndBucket: () => getOptsAndBucket,
	metadataUpdatedEvent: () => metadataUpdatedEvent,
	onObjectArchived: () => onObjectArchived,
	onObjectDeleted: () => onObjectDeleted,
	onObjectFinalized: () => onObjectFinalized,
	onObjectMetadataUpdated: () => onObjectMetadataUpdated,
	onOperation: () => onOperation
});
/** @internal */
const archivedEvent = "google.cloud.storage.object.v1.archived";
/** @internal */
const finalizedEvent = "google.cloud.storage.object.v1.finalized";
/** @internal */
const deletedEvent = "google.cloud.storage.object.v1.deleted";
/** @internal */
const metadataUpdatedEvent = "google.cloud.storage.object.v1.metadataUpdated";
/**
* Event handler sent only when a bucket has enabled object versioning.
* This event indicates that the live version of an object has become an
* archived version, either because it was archived or because it was
* overwritten by the upload of an object of the same name.
*
* @param bucketOrOptsOrHandler - Options or string that may (or may not) define the bucket to be used.
* @param handler - Event handler which is run every time a Google Cloud Storage archival occurs.
*/
function onObjectArchived(bucketOrOptsOrHandler, handler) {
	return onOperation(archivedEvent, bucketOrOptsOrHandler, handler);
}
/**
* Event handler which fires every time a Google Cloud Storage object
* creation occurs.
*
* Sent when a new object (or a new generation of an existing object)
* is successfully created in the bucket. This includes copying or rewriting
* an existing object. A failed upload does not trigger this event.
*
* @param bucketOrOptsOrHandler - Options or string that may (or may not) define the bucket to be used.
* @param handler - Event handler which is run every time a Google Cloud Storage object creation occurs.
*/
function onObjectFinalized(bucketOrOptsOrHandler, handler) {
	return onOperation(finalizedEvent, bucketOrOptsOrHandler, handler);
}
/**
* Event handler which fires every time a Google Cloud Storage deletion occurs.
*
* Sent when an object has been permanently deleted. This includes objects
* that are overwritten or are deleted as part of the bucket's lifecycle
* configuration. For buckets with object versioning enabled, this is not
* sent when an object is archived, even if archival occurs
* via the `storage.objects.delete` method.
*
* @param bucketOrOptsOrHandler - Options or string that may (or may not) define the bucket to be used.
* @param handler - Event handler which is run every time a Google Cloud Storage object deletion occurs.
*/
function onObjectDeleted(bucketOrOptsOrHandler, handler) {
	return onOperation(deletedEvent, bucketOrOptsOrHandler, handler);
}
/**
* Event handler which fires every time the metadata of an existing object
* changes.
*
* @param bucketOrOptsOrHandler - Options or string that may (or may not) define the bucket to be used.
* @param handler - Event handler which is run every time a Google Cloud Storage object metadata update occurs.
*/
function onObjectMetadataUpdated(bucketOrOptsOrHandler, handler) {
	return onOperation(metadataUpdatedEvent, bucketOrOptsOrHandler, handler);
}
/** @internal */
function onOperation(eventType, bucketOrOptsOrHandler, handler) {
	if (typeof bucketOrOptsOrHandler === "function") {
		handler = bucketOrOptsOrHandler;
		bucketOrOptsOrHandler = {};
	}
	const [opts, bucket] = getOptsAndBucket(bucketOrOptsOrHandler);
	const func = (raw) => {
		return wrapTraceContext(withInit(handler))(raw);
	};
	func.run = handler;
	Object.defineProperty(func, "__trigger", { get: () => {
		const baseOpts = optionsToTriggerAnnotations(getGlobalOptions());
		const specificOpts = optionsToTriggerAnnotations(opts);
		return {
			platform: "gcfv2",
			...baseOpts,
			...specificOpts,
			labels: {
				...baseOpts?.labels,
				...specificOpts?.labels
			},
			eventTrigger: {
				eventType,
				resource: bucket
			}
		};
	} });
	func.__endpoint = {};
	Object.defineProperty(func, "__endpoint", { get: () => {
		const baseOpts = optionsToEndpoint(getGlobalOptions());
		const specificOpts = optionsToEndpoint(opts);
		const endpoint = {
			platform: "gcfv2",
			...initV2Endpoint(getGlobalOptions(), opts),
			...baseOpts,
			...specificOpts,
			labels: {
				...baseOpts?.labels,
				...specificOpts?.labels
			},
			eventTrigger: {
				eventType,
				eventFilters: { bucket },
				retry: opts.retry ?? false
			}
		};
		copyIfPresent(endpoint.eventTrigger, opts, "retry", "retry");
		return endpoint;
	} });
	return func;
}
/** @internal */
function getOptsAndBucket(bucketOrOpts) {
	let bucket;
	let opts;
	if (typeof bucketOrOpts === "string" || "value" in bucketOrOpts) {
		bucket = bucketOrOpts;
		opts = {};
	} else {
		bucket = bucketOrOpts.bucket || firebaseConfig()?.storageBucket;
		opts = { ...bucketOrOpts };
		delete opts.bucket;
	}
	if (!bucket) {
		throw new Error("Missing bucket name. If you are unit testing, please provide a bucket name" + " by providing bucket name directly in the event handler or by setting process.env.FIREBASE_CONFIG.");
	}
	if (typeof bucket === "string" && !/^[a-z\d][a-z\d\\._-]{1,230}[a-z\d]$/.test(bucket)) {
		throw new Error(`Invalid bucket name ${bucket}`);
	}
	return [opts, bucket];
}

//#endregion
export { archivedEvent, deletedEvent, finalizedEvent, getOptsAndBucket, metadataUpdatedEvent, onObjectArchived, onObjectDeleted, onObjectFinalized, onObjectMetadataUpdated, onOperation, storage_exports };