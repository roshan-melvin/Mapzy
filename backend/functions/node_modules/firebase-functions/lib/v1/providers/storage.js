const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_common_config = require('../../common/config.js');
const require_v1_cloud_functions = require('../cloud-functions.js');

//#region src/v1/providers/storage.ts
var storage_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	BucketBuilder: () => BucketBuilder,
	ObjectBuilder: () => ObjectBuilder,
	_bucketWithOptions: () => _bucketWithOptions,
	_objectWithOptions: () => _objectWithOptions,
	bucket: () => bucket,
	object: () => object,
	provider: () => provider,
	service: () => service
});
/** @internal */
const provider = "google.storage";
/** @internal */
const service = "storage.googleapis.com";
/**
* Registers a Cloud Function scoped to a specific storage bucket.
*
* @param bucket Name of the bucket to which this Cloud Function is
*   scoped.
*
* @returns Storage bucket builder interface.
*/
function bucket(bucket$1) {
	return _bucketWithOptions({}, bucket$1);
}
/**
* Registers a Cloud Function scoped to the default storage bucket for the
* project.
*
* @returns Storage object builder interface.
*/
function object() {
	return _objectWithOptions({});
}
/** @internal */
function _bucketWithOptions(options, bucket$1) {
	const resourceGetter = () => {
		bucket$1 = bucket$1 || require_common_config.firebaseConfig().storageBucket;
		if (!bucket$1) {
			throw new Error("Missing bucket name. If you are unit testing, please provide a bucket name" + " through `functions.storage.bucket(bucketName)`, or set process.env.FIREBASE_CONFIG.");
		}
		if (!/^[a-z\d][a-z\d\\._-]{1,230}[a-z\d]$/.test(bucket$1)) {
			throw new Error(`Invalid bucket name ${bucket$1}`);
		}
		return `projects/_/buckets/${bucket$1}`;
	};
	return new BucketBuilder(resourceGetter, options);
}
/** @internal */
function _objectWithOptions(options) {
	return _bucketWithOptions(options).object();
}
/**
* The Google Cloud Storage bucket builder interface.
*
* Access via `functions.storage.bucket()`.
*/
var BucketBuilder = class {
	/** @internal */
	constructor(triggerResource, options) {
		this.triggerResource = triggerResource;
		this.options = options;
	}
	/**
	* Event handler which fires every time a Google Cloud Storage change occurs.
	*
	* @returns Storage object builder interface scoped to the specified storage
	*   bucket.
	*/
	object() {
		return new ObjectBuilder(this.triggerResource, this.options);
	}
};
/**
* The Google Cloud Storage object builder interface.
*
* Access via `functions.storage.object()`.
*/
var ObjectBuilder = class {
	/** @internal */
	constructor(triggerResource, options) {
		this.triggerResource = triggerResource;
		this.options = options;
	}
	/**
	* Event handler sent only when a bucket has enabled object versioning.
	* This event indicates that the live version of an object has become an
	* archived version, either because it was archived or because it was
	* overwritten by the upload of an object of the same name.
	*
	* @param handler Event handler which is run every time a Google Cloud Storage
	*   archival occurs.
	*
	* @returns A function which you can export and deploy.
	*/
	onArchive(handler) {
		return this.onOperation(handler, "object.archive");
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
	* @param handler Event handler which is run every time a Google Cloud Storage
	*   deletion occurs.
	*
	* @returns A function which you can export and deploy.
	*/
	onDelete(handler) {
		return this.onOperation(handler, "object.delete");
	}
	/**
	* Event handler which fires every time a Google Cloud Storage object
	* creation occurs.
	*
	* Sent when a new object (or a new generation of an existing object)
	* is successfully created in the bucket. This includes copying or rewriting
	* an existing object. A failed upload does not trigger this event.
	*
	* @param handler Event handler which is run every time a Google Cloud Storage
	*   object creation occurs.
	*
	* @returns A function which you can export and deploy.
	*/
	onFinalize(handler) {
		return this.onOperation(handler, "object.finalize");
	}
	/**
	* Event handler which fires every time the metadata of an existing object
	* changes.
	*
	* @param handler Event handler which is run every time a Google Cloud Storage
	*   metadata update occurs.
	*
	* @returns A function which you can export and deploy.
	*/
	onMetadataUpdate(handler) {
		return this.onOperation(handler, "object.metadataUpdate");
	}
	/** @hidden */
	onOperation(handler, eventType) {
		return require_v1_cloud_functions.makeCloudFunction({
			handler,
			provider,
			service,
			eventType,
			triggerResource: this.triggerResource,
			options: this.options
		});
	}
};

//#endregion
exports.BucketBuilder = BucketBuilder;
exports.ObjectBuilder = ObjectBuilder;
exports._bucketWithOptions = _bucketWithOptions;
exports._objectWithOptions = _objectWithOptions;
exports.bucket = bucket;
exports.object = object;
exports.provider = provider;
exports.service = service;
Object.defineProperty(exports, 'storage_exports', {
  enumerable: true,
  get: function () {
    return storage_exports;
  }
});