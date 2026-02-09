import { warn } from "../logger/index.mjs";
import { RESET_VALUE, ResetValue } from "../common/options.mjs";
import { initV1Endpoint, initV1ScheduleTrigger } from "../runtime/manifest.mjs";
import { Change } from "../common/change.mjs";
import { convertIfPresent, copyIfPresent, durationFromSeconds, serviceAccountFromShorthand } from "../common/encoding.mjs";
import { withInit } from "../common/onInit.mjs";
import { DEFAULT_FAILURE_POLICY } from "./function-configuration.mjs";

//#region src/v1/cloud-functions.ts
/** @internal */
const WILDCARD_REGEX = new RegExp("{[^/{}]*}", "g");
/** @internal */
function makeCloudFunction({ contextOnlyHandler, dataConstructor = (raw) => raw.data, eventType, handler, labels = {}, legacyEventType, options = {}, provider, service, triggerResource }) {
	handler = withInit(handler ?? contextOnlyHandler);
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
			warn("Function returned undefined, expected Promise or value");
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
			...initV1Endpoint(options),
			...optionsToEndpoint(options)
		};
		if (options.schedule) {
			endpoint.scheduleTrigger = initV1ScheduleTrigger(options.schedule.schedule, options);
			copyIfPresent(endpoint.scheduleTrigger, options.schedule, "timeZone");
			copyIfPresent(endpoint.scheduleTrigger.retryConfig, options.schedule.retryConfig, "retryCount", "maxDoublings", "maxBackoffDuration", "maxRetryDuration", "minBackoffDuration");
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
	copyIfPresent(trigger, options, "regions", "schedule", "minInstances", "maxInstances", "ingressSettings", "vpcConnectorEgressSettings", "vpcConnector", "labels", "secrets");
	convertIfPresent(trigger, options, "failurePolicy", "failurePolicy", (policy) => {
		if (policy === false) {
			return undefined;
		} else if (policy === true) {
			return DEFAULT_FAILURE_POLICY;
		} else {
			return policy;
		}
	});
	convertIfPresent(trigger, options, "timeout", "timeoutSeconds", durationFromSeconds);
	convertIfPresent(trigger, options, "availableMemoryMb", "memory", (mem) => {
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
	convertIfPresent(trigger, options, "serviceAccountEmail", "serviceAccount", serviceAccountFromShorthand);
	return trigger;
}
function optionsToEndpoint(options) {
	const endpoint = {};
	copyIfPresent(endpoint, options, "omit", "minInstances", "maxInstances", "ingressSettings", "labels", "timeoutSeconds");
	convertIfPresent(endpoint, options, "region", "regions");
	convertIfPresent(endpoint, options, "serviceAccountEmail", "serviceAccount", (sa) => sa);
	convertIfPresent(endpoint, options, "secretEnvironmentVariables", "secrets", (secrets) => secrets.map((secret) => ({ key: typeof secret === "string" ? secret : secret.name })));
	if (options?.vpcConnector !== undefined) {
		if (options.vpcConnector === null || options.vpcConnector instanceof ResetValue) {
			endpoint.vpc = RESET_VALUE;
		} else {
			const vpc = { connector: options.vpcConnector };
			convertIfPresent(vpc, options, "egressSettings", "vpcConnectorEgressSettings");
			endpoint.vpc = vpc;
		}
	}
	convertIfPresent(endpoint, options, "availableMemoryMb", "memory", (mem) => {
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
export { Change, makeCloudFunction, optionsToEndpoint, optionsToTrigger };