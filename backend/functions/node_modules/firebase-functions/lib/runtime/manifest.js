const require_params_types = require('../params/types.js');
require('../params/index.js');
const require_common_options = require('../common/options.js');

//#region src/runtime/manifest.ts
/**
* Returns the JSON representation of a ManifestStack, which has CEL
* expressions in its options as object types, with its expressions
* transformed into the actual CEL strings.
*
* @alpha
*/
function stackToWire(stack) {
	const wireStack = stack;
	const traverse = function traverse$1(obj) {
		for (const [key, val] of Object.entries(obj)) {
			if (val instanceof require_params_types.Expression) {
				obj[key] = val.toCEL();
			} else if (val instanceof require_common_options.ResetValue) {
				obj[key] = val.toJSON();
			} else if (typeof val === "object" && val !== null) {
				traverse$1(val);
			}
		}
	};
	traverse(wireStack.endpoints);
	return wireStack;
}
const RESETTABLE_OPTIONS = {
	availableMemoryMb: null,
	timeoutSeconds: null,
	minInstances: null,
	maxInstances: null,
	ingressSettings: null,
	concurrency: null,
	serviceAccountEmail: null,
	vpc: null
};
function initEndpoint(resetOptions, ...opts) {
	const endpoint = {};
	if (opts.every((opt) => !opt?.preserveExternalChanges)) {
		for (const key of Object.keys(resetOptions)) {
			endpoint[key] = require_common_options.RESET_VALUE;
		}
	}
	return endpoint;
}
/**
* @internal
*/
function initV1Endpoint(...opts) {
	const { concurrency,...resetOpts } = RESETTABLE_OPTIONS;
	return initEndpoint({ ...resetOpts }, ...opts);
}
/**
* @internal
*/
function initV2Endpoint(...opts) {
	return initEndpoint(RESETTABLE_OPTIONS, ...opts);
}
const RESETTABLE_RETRY_CONFIG_OPTIONS = {
	maxAttempts: null,
	maxDoublings: null,
	maxBackoffSeconds: null,
	maxRetrySeconds: null,
	minBackoffSeconds: null
};
const RESETTABLE_RATE_LIMITS_OPTIONS = {
	maxConcurrentDispatches: null,
	maxDispatchesPerSecond: null
};
/**
* @internal
*/
function initTaskQueueTrigger(...opts) {
	const taskQueueTrigger = {
		retryConfig: {},
		rateLimits: {}
	};
	if (opts.every((opt) => !opt?.preserveExternalChanges)) {
		for (const key of Object.keys(RESETTABLE_RETRY_CONFIG_OPTIONS)) {
			taskQueueTrigger.retryConfig[key] = require_common_options.RESET_VALUE;
		}
		for (const key of Object.keys(RESETTABLE_RATE_LIMITS_OPTIONS)) {
			taskQueueTrigger.rateLimits[key] = require_common_options.RESET_VALUE;
		}
	}
	return taskQueueTrigger;
}
const RESETTABLE_V1_SCHEDULE_OPTIONS = {
	retryCount: null,
	maxDoublings: null,
	maxRetryDuration: null,
	maxBackoffDuration: null,
	minBackoffDuration: null
};
const RESETTABLE_V2_SCHEDULE_OPTIONS = {
	retryCount: null,
	maxDoublings: null,
	maxRetrySeconds: null,
	minBackoffSeconds: null,
	maxBackoffSeconds: null
};
function initScheduleTrigger(resetOptions, schedule, ...opts) {
	let scheduleTrigger = {
		schedule,
		retryConfig: {}
	};
	if (opts.every((opt) => !opt?.preserveExternalChanges)) {
		for (const key of Object.keys(resetOptions)) {
			scheduleTrigger.retryConfig[key] = require_common_options.RESET_VALUE;
		}
		scheduleTrigger = {
			...scheduleTrigger,
			timeZone: require_common_options.RESET_VALUE
		};
	}
	return scheduleTrigger;
}
/**
* @internal
*/
function initV1ScheduleTrigger(schedule, ...opts) {
	return initScheduleTrigger(RESETTABLE_V1_SCHEDULE_OPTIONS, schedule, ...opts);
}
/**
* @internal
*/
function initV2ScheduleTrigger(schedule, ...opts) {
	return initScheduleTrigger(RESETTABLE_V2_SCHEDULE_OPTIONS, schedule, ...opts);
}

//#endregion
exports.initTaskQueueTrigger = initTaskQueueTrigger;
exports.initV1Endpoint = initV1Endpoint;
exports.initV1ScheduleTrigger = initV1ScheduleTrigger;
exports.initV2Endpoint = initV2Endpoint;
exports.initV2ScheduleTrigger = initV2ScheduleTrigger;
exports.stackToWire = stackToWire;