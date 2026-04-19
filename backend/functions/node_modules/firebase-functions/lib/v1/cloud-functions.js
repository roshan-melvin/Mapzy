const require_logger_index = require('../logger/index.js');
const require_common_options = require('../common/options.js');
const require_runtime_manifest = require('../runtime/manifest.js');
const require_common_change = require('../common/change.js');
const require_common_encoding = require('../common/encoding.js');
const require_common_onInit = require('../common/onInit.js');
const require_v1_function_configuration = require('./function-configuration.js');

//#region src/v1/cloud-functions.ts
/** @internal */
const WILDCARD_REGEX = new RegExp("{[^/{}]*}", "g");
/** @internal */
function makeCloudFunction({ contextOnlyHandler, dataConstructor = (raw) => raw.data, eventType, handler, labels = {}, legacyEventType, options = {}, provider, service, triggerResource }) {
	handler = require_common_onInit.withInit(handler ?? contextOnlyHandler);
	const cloudFunction = (data, context) => {
		if (legacyEventType && context.eventType === legacyEventType) {
			context.eventType = provider + "." + eventType;
			context.resource = {
				service,
				name: context.resource
			};
		}
		const event = {
			data,
			context
		};
		if (provider === "google.firebase.database") {
			context.authType = _detectAuthType(event);
			if (context.authType !== "ADMIN") {
				context.auth = _makeAuth(event, context.authType);
			} else {
				delete context.auth;
			}
		}
		if (triggerResource() == null) {
			Object.defineProperty(context, "params", { get: () => {
				throw new Error("context.params is not available when using the handler namespace.");
			} });
		} else {
			context.params = context.params || _makeParams(context, triggerResource);
		}
		let promise;
		if (labels && labels["deployment-scheduled"]) {
			promise = contextOnlyHandler(context);
		} else {
			const dataOrChange = dataConstructor(event);
			promise = handler(dataOrChange, context);
		}
		if (typeof promise === "undefined") {
			require_logger_index.warn("Function returned undefined, expected Promise or value");
		}
		return Promise.resolve(promise);
	};
	Object.defineProperty(cloudFunction, "__trigger", { get: () => {
		if (triggerResource() == null) {
			return {};
		}
		const trigger = {
			...optionsToTrigger(options),
			eventTrigger: {
				resource: triggerResource(),
				eventType: legacyEventType || provider + "." + eventType,
				service
			}
		};
		if (!!labels && Object.keys(labels).length) {
			trigger.labels = {
				...trigger.labels,
				...labels
			};
		}
		return trigger;
	} });
	Object.defineProperty(cloudFunction, "__endpoint", { get: () => {
		if (triggerResource() == null) {
			return undefined;
		}
		const endpoint = {
			platform: "gcfv1",
			...require_runtime_manifest.initV1Endpoint(options),
			...optionsToEndpoint(options)
		};
		if (options.schedule) {
			endpoint.scheduleTrigger = require_runtime_manifest.initV1ScheduleTrigger(options.schedule.schedule, options);
			require_common_encoding.copyIfPresent(endpoint.scheduleTrigger, options.schedule, "timeZone");
			require_common_encoding.copyIfPresent(endpoint.scheduleTrigger.retryConfig, options.schedule.retryConfig, "retryCount", "maxDoublings", "maxBackoffDuration", "maxRetryDuration", "minBackoffDuration");
		} else {
			endpoint.eventTrigger = {
				eventType: legacyEventType || provider + "." + eventType,
				eventFilters: { resource: triggerResource() },
				retry: !!options.failurePolicy
			};
		}
		endpoint.labels = { ...endpoint.labels };
		return endpoint;
	} });
	if (options.schedule) {
		cloudFunction.__requiredAPIs = [{
			api: "cloudscheduler.googleapis.com",
			reason: "Needed for scheduled functions."
		}];
	}
	cloudFunction.run = handler || contextOnlyHandler;
	return cloudFunction;
}
function _makeParams(context, triggerResourceGetter) {
	if (context.params) {
		return context.params;
	}
	if (!context.resource) {
		return {};
	}
	const triggerResource = triggerResourceGetter();
	const wildcards = triggerResource.match(WILDCARD_REGEX);
	const params = {};
	const eventResourceParts = context?.resource?.name?.split?.("/");
	if (wildcards && eventResourceParts) {
		const triggerResourceParts = triggerResource.split("/");
		for (const wildcard of wildcards) {
			const wildcardNoBraces = wildcard.slice(1, -1);
			const position = triggerResourceParts.indexOf(wildcard);
			params[wildcardNoBraces] = eventResourceParts[position];
		}
	}
	return params;
}
function _makeAuth(event, authType) {
	if (authType === "UNAUTHENTICATED") {
		return null;
	}
	return {
		uid: event.context?.auth?.variable?.uid,
		token: event.context?.auth?.variable?.token
	};
}
function _detectAuthType(event) {
	if (event.context?.auth?.admin) {
		return "ADMIN";
	}
	if (event.context?.auth?.variable) {
		return "USER";
	}
	return "UNAUTHENTICATED";
}
/** @hidden */
function optionsToTrigger(options) {
	const trigger = {};
	require_common_encoding.copyIfPresent(trigger, options, "regions", "schedule", "minInstances", "maxInstances", "ingressSettings", "vpcConnectorEgressSettings", "vpcConnector", "labels", "secrets");
	require_common_encoding.convertIfPresent(trigger, options, "failurePolicy", "failurePolicy", (policy) => {
		if (policy === false) {
			return undefined;
		} else if (policy === true) {
			return require_v1_function_configuration.DEFAULT_FAILURE_POLICY;
		} else {
			return policy;
		}
	});
	require_common_encoding.convertIfPresent(trigger, options, "timeout", "timeoutSeconds", require_common_encoding.durationFromSeconds);
	require_common_encoding.convertIfPresent(trigger, options, "availableMemoryMb", "memory", (mem) => {
		const memoryLookup = {
			"128MB": 128,
			"256MB": 256,
			"512MB": 512,
			"1GB": 1024,
			"2GB": 2048,
			"4GB": 4096,
			"8GB": 8192
		};
		return memoryLookup[mem];
	});
	require_common_encoding.convertIfPresent(trigger, options, "serviceAccountEmail", "serviceAccount", require_common_encoding.serviceAccountFromShorthand);
	return trigger;
}
function optionsToEndpoint(options) {
	const endpoint = {};
	require_common_encoding.copyIfPresent(endpoint, options, "omit", "minInstances", "maxInstances", "ingressSettings", "labels", "timeoutSeconds");
	require_common_encoding.convertIfPresent(endpoint, options, "region", "regions");
	require_common_encoding.convertIfPresent(endpoint, options, "serviceAccountEmail", "serviceAccount", (sa) => sa);
	require_common_encoding.convertIfPresent(endpoint, options, "secretEnvironmentVariables", "secrets", (secrets) => secrets.map((secret) => ({ key: typeof secret === "string" ? secret : secret.name })));
	if (options?.vpcConnector !== undefined) {
		if (options.vpcConnector === null || options.vpcConnector instanceof require_common_options.ResetValue) {
			endpoint.vpc = require_common_options.RESET_VALUE;
		} else {
			const vpc = { connector: options.vpcConnector };
			require_common_encoding.convertIfPresent(vpc, options, "egressSettings", "vpcConnectorEgressSettings");
			endpoint.vpc = vpc;
		}
	}
	require_common_encoding.convertIfPresent(endpoint, options, "availableMemoryMb", "memory", (mem) => {
		const memoryLookup = {
			"128MB": 128,
			"256MB": 256,
			"512MB": 512,
			"1GB": 1024,
			"2GB": 2048,
			"4GB": 4096,
			"8GB": 8192
		};
		return typeof mem === "object" ? mem : memoryLookup[mem];
	});
	return endpoint;
}

//#endregion
exports.Change = require_common_change.Change;
exports.makeCloudFunction = makeCloudFunction;
exports.optionsToEndpoint = optionsToEndpoint;
exports.optionsToTrigger = optionsToTrigger;