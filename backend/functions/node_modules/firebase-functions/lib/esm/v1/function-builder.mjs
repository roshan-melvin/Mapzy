import { Expression } from "../params/types.mjs";
import { ResetValue } from "../common/options.mjs";
import { INGRESS_SETTINGS_OPTIONS, MAX_NUMBER_USER_LABELS, MAX_TIMEOUT_SECONDS, VALID_MEMORY_OPTIONS, VPC_EGRESS_SETTINGS_OPTIONS } from "./function-configuration.mjs";
import { _eventWithOptions } from "./providers/analytics.mjs";
import { _userWithOptions } from "./providers/auth.mjs";
import { _instanceWithOptions, _refWithOptions } from "./providers/database.mjs";
import { _databaseWithOptions, _documentWithOptions, _namespaceWithOptions } from "./providers/firestore.mjs";
import { _onCallWithOptions, _onRequestWithOptions } from "./providers/https.mjs";
import { _scheduleWithOptions, _topicWithOptions } from "./providers/pubsub.mjs";
import { _onUpdateWithOptions } from "./providers/remoteConfig.mjs";
import { _bucketWithOptions, _objectWithOptions } from "./providers/storage.mjs";
import { TaskQueueBuilder } from "./providers/tasks.mjs";
import { _testMatrixWithOpts } from "./providers/testLab.mjs";

//#region src/v1/function-builder.ts
/**
* Assert that the runtime options passed in are valid.
* @param runtimeOptions object containing memory and timeout information.
* @throws { Error } Memory and TimeoutSeconds values must be valid.
*/
function assertRuntimeOptionsValid(runtimeOptions) {
	const mem = runtimeOptions.memory;
	if (mem && typeof mem !== "object" && !VALID_MEMORY_OPTIONS.includes(mem)) {
		throw new Error(`The only valid memory allocation values are: ${VALID_MEMORY_OPTIONS.join(", ")}`);
	}
	if (typeof runtimeOptions.timeoutSeconds === "number" && (runtimeOptions.timeoutSeconds > MAX_TIMEOUT_SECONDS || runtimeOptions.timeoutSeconds < 0)) {
		throw new Error(`TimeoutSeconds must be between 0 and ${MAX_TIMEOUT_SECONDS}`);
	}
	if (runtimeOptions.ingressSettings && !(runtimeOptions.ingressSettings instanceof ResetValue) && !INGRESS_SETTINGS_OPTIONS.includes(runtimeOptions.ingressSettings)) {
		throw new Error(`The only valid ingressSettings values are: ${INGRESS_SETTINGS_OPTIONS.join(",")}`);
	}
	if (runtimeOptions.vpcConnectorEgressSettings && !(runtimeOptions.vpcConnectorEgressSettings instanceof ResetValue) && !VPC_EGRESS_SETTINGS_OPTIONS.includes(runtimeOptions.vpcConnectorEgressSettings)) {
		throw new Error(`The only valid vpcConnectorEgressSettings values are: ${VPC_EGRESS_SETTINGS_OPTIONS.join(",")}`);
	}
	validateFailurePolicy(runtimeOptions.failurePolicy);
	const serviceAccount = runtimeOptions.serviceAccount;
	if (serviceAccount && !(serviceAccount === "default" || serviceAccount instanceof ResetValue || serviceAccount instanceof Expression || serviceAccount.includes("@"))) {
		throw new Error(`serviceAccount must be set to 'default', a string expression, a service account email, or '{serviceAccountName}@'`);
	}
	if (runtimeOptions.labels) {
		if (Object.keys(runtimeOptions.labels).length > MAX_NUMBER_USER_LABELS) {
			throw new Error(`A function must not have more than ${MAX_NUMBER_USER_LABELS} user-defined labels.`);
		}
		const reservedKeys = Object.keys(runtimeOptions.labels).filter((key) => key.startsWith("deployment") || key.startsWith("firebase"));
		if (reservedKeys.length) {
			throw new Error(`Invalid labels: ${reservedKeys.join(", ")}. Labels may not start with reserved names 'deployment' or 'firebase'`);
		}
		const invalidLengthKeys = Object.keys(runtimeOptions.labels).filter((key) => key.length < 1 || key.length > 63);
		if (invalidLengthKeys.length > 0) {
			throw new Error(`Invalid labels: ${invalidLengthKeys.join(", ")}. Label keys must be between 1 and 63 characters in length.`);
		}
		const invalidLengthValues = Object.values(runtimeOptions.labels).filter((value) => value.length > 63);
		if (invalidLengthValues.length > 0) {
			throw new Error(`Invalid labels: ${invalidLengthValues.join(", ")}. Label values must be less than 64 charcters.`);
		}
		const validKeyPattern = /^[\p{Ll}\p{Lo}][\p{Ll}\p{Lo}\p{N}_-]{0,62}$/u;
		const invalidKeys = Object.keys(runtimeOptions.labels).filter((key) => !validKeyPattern.test(key));
		if (invalidKeys.length > 0) {
			throw new Error(`Invalid labels: ${invalidKeys.join(", ")}. Label keys can only contain lowercase letters, international characters, numbers, _ or -, and must start with a letter.`);
		}
		const validValuePattern = /^[\p{Ll}\p{Lo}\p{N}_-]{0,63}$/u;
		const invalidValues = Object.values(runtimeOptions.labels).filter((value) => !validValuePattern.test(value));
		if (invalidValues.length > 0) {
			throw new Error(`Invalid labels: ${invalidValues.join(", ")}. Label values can only contain lowercase letters, international characters, numbers, _ or -.`);
		}
	}
	if (typeof runtimeOptions.invoker === "string" && runtimeOptions.invoker.length === 0) {
		throw new Error("Invalid service account for function invoker, must be a non-empty string");
	}
	if (runtimeOptions.invoker !== undefined && Array.isArray(runtimeOptions.invoker)) {
		if (runtimeOptions.invoker.length === 0) {
			throw new Error("Invalid invoker array, must contain at least 1 service account entry");
		}
		for (const serviceAccount$1 of runtimeOptions.invoker) {
			if (serviceAccount$1.length === 0) {
				throw new Error("Invalid invoker array, a service account must be a non-empty string");
			}
			if (serviceAccount$1 === "public") {
				throw new Error("Invalid invoker array, a service account cannot be set to the 'public' identifier");
			}
			if (serviceAccount$1 === "private") {
				throw new Error("Invalid invoker array, a service account cannot be set to the 'private' identifier");
			}
		}
	}
	if (runtimeOptions.secrets !== undefined) {
		const invalidSecrets = runtimeOptions.secrets.filter((s) => !/^[A-Za-z\d\-_]+$/.test(typeof s === "string" ? s : s.name));
		if (invalidSecrets.length > 0) {
			throw new Error(`Invalid secrets: ${invalidSecrets.join(",")}. ` + "Secret must be configured using the resource id (e.g. API_KEY)");
		}
	}
	if ("allowInvalidAppCheckToken" in runtimeOptions) {
		throw new Error("runWith option \"allowInvalidAppCheckToken\" has been inverted and " + "renamed \"enforceAppCheck\"");
	}
	return true;
}
function validateFailurePolicy(policy) {
	if (typeof policy === "boolean" || typeof policy === "undefined") {
		return;
	}
	if (typeof policy !== "object") {
		throw new Error(`failurePolicy must be a boolean or an object.`);
	}
	const retry = policy.retry;
	if (typeof retry !== "object" || Object.keys(retry).length) {
		throw new Error("failurePolicy.retry must be an empty object.");
	}
}
/**
* Assert regions specified are valid.
* @param regions list of regions.
* @throws { Error } Regions must be in list of supported regions.
*/
function assertRegionsAreValid(regions) {
	if (!regions.length) {
		throw new Error("You must specify at least one region");
	}
	return true;
}
/**
* Configure the regions that the function is deployed to.
* @param regions One of more region strings.
* @example
* functions.region('us-east1')
* @example
* functions.region('us-east1', 'us-central1')
*/
function region(...regions) {
	if (assertRegionsAreValid(regions)) {
		return new FunctionBuilder({ regions });
	}
}
/**
* Configure runtime options for the function.
* @param runtimeOptions Object with optional fields:
* 1. `memory`: amount of memory to allocate to the function, possible values
*    are: '128MB', '256MB', '512MB', '1GB', '2GB', '4GB', and '8GB'.
* 2. `timeoutSeconds`: timeout for the function in seconds, possible values are
*    0 to 540.
* 3. `failurePolicy`: failure policy of the function, with boolean `true` being
*    equivalent to providing an empty retry object.
* 4. `vpcConnector`: id of a VPC connector in same project and region.
* 5. `vpcConnectorEgressSettings`: when a vpcConnector is set, control which
*    egress traffic is sent through the vpcConnector.
* 6. `serviceAccount`: Specific service account for the function.
* 7. `ingressSettings`: ingress settings for the function, which control where a HTTPS
*    function can be called from.
*
* Value must not be null.
*/
function runWith(runtimeOptions) {
	if (assertRuntimeOptionsValid(runtimeOptions)) {
		return new FunctionBuilder(runtimeOptions);
	}
}
var FunctionBuilder = class {
	constructor(options) {
		this.options = options;
	}
	/**
	* Configure the regions that the function is deployed to.
	* @param regions One or more region strings.
	* @example
	* functions.region('us-east1')
	* @example
	* functions.region('us-east1', 'us-central1')
	*/
	region(...regions) {
		if (assertRegionsAreValid(regions)) {
			this.options.regions = regions;
			return this;
		}
	}
	/**
	* Configure runtime options for the function.
	* @param runtimeOptions Object with optional fields:
	* 1. `memory`: amount of memory to allocate to the function, possible values
	*    are: '128MB', '256MB', '512MB', '1GB', '2GB', '4GB', and '8GB'.
	* 2. `timeoutSeconds`: timeout for the function in seconds, possible values are
	*    0 to 540.
	* 3. `failurePolicy`: failure policy of the function, with boolean `true` being
	*    equivalent to providing an empty retry object.
	* 4. `vpcConnector`: id of a VPC connector in the same project and region
	* 5. `vpcConnectorEgressSettings`: when a `vpcConnector` is set, control which
	*    egress traffic is sent through the `vpcConnector`.
	*
	* Value must not be null.
	*/
	runWith(runtimeOptions) {
		if (assertRuntimeOptionsValid(runtimeOptions)) {
			this.options = {
				...this.options,
				...runtimeOptions
			};
			return this;
		}
	}
	get https() {
		if (this.options.failurePolicy !== undefined) {
			console.warn("RuntimeOptions.failurePolicy is not supported in https functions.");
		}
		return {
			onRequest: (handler) => _onRequestWithOptions(handler, this.options),
			onCall: (handler) => _onCallWithOptions(handler, this.options)
		};
	}
	get tasks() {
		return { taskQueue: (options) => {
			return new TaskQueueBuilder(options, this.options);
		} };
	}
	get database() {
		return {
			instance: (instance) => _instanceWithOptions(instance, this.options),
			ref: (path) => _refWithOptions(path, this.options)
		};
	}
	get firestore() {
		return {
			document: (path) => _documentWithOptions(path, this.options),
			namespace: (namespace) => _namespaceWithOptions(namespace, this.options),
			database: (database) => _databaseWithOptions(database, this.options)
		};
	}
	get analytics() {
		return { event: (analyticsEventType) => _eventWithOptions(analyticsEventType, this.options) };
	}
	get remoteConfig() {
		return { onUpdate: (handler) => _onUpdateWithOptions(handler, this.options) };
	}
	get storage() {
		return {
			bucket: (bucket) => _bucketWithOptions(this.options, bucket),
			object: () => _objectWithOptions(this.options)
		};
	}
	get pubsub() {
		return {
			topic: (topic) => _topicWithOptions(topic, this.options),
			schedule: (schedule) => _scheduleWithOptions(schedule, this.options)
		};
	}
	get auth() {
		return { user: (userOptions) => _userWithOptions(this.options, userOptions) };
	}
	get testLab() {
		return { testMatrix: () => _testMatrixWithOpts(this.options) };
	}
};

//#endregion
export { FunctionBuilder, region, runWith };