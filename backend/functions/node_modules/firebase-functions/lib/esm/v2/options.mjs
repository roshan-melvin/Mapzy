import { warn } from "../logger/index.mjs";
import { declaredParams } from "../params/index.mjs";
import { RESET_VALUE, ResetValue } from "../common/options.mjs";
import { convertIfPresent, copyIfPresent, durationFromSeconds, serviceAccountFromShorthand } from "../common/encoding.mjs";

//#region src/v2/options.ts
const MemoryOptionToMB = {
	"128MiB": 128,
	"256MiB": 256,
	"512MiB": 512,
	"1GiB": 1024,
	"2GiB": 2048,
	"4GiB": 4096,
	"8GiB": 8192,
	"16GiB": 16384,
	"32GiB": 32768
};
let globalOptions;
/**
* Sets default options for all functions written using the 2nd gen SDK.
* @param options Options to set as default
*/
function setGlobalOptions(options) {
	if (globalOptions) {
		warn("Calling setGlobalOptions twice leads to undefined behavior");
	}
	globalOptions = options;
}
/**
* Get the currently set default options.
* Used only for trigger generation.
* @internal
*/
function getGlobalOptions() {
	return globalOptions || {};
}
/**
* Apply GlobalOptions to trigger definitions.
* @internal
*/
function optionsToTriggerAnnotations(opts) {
	const annotation = {};
	copyIfPresent(annotation, opts, "concurrency", "minInstances", "maxInstances", "ingressSettings", "labels", "vpcConnector", "vpcConnectorEgressSettings", "secrets");
	convertIfPresent(annotation, opts, "availableMemoryMb", "memory", (mem) => {
		return MemoryOptionToMB[mem];
	});
	convertIfPresent(annotation, opts, "regions", "region", (region) => {
		if (typeof region === "string") {
			return [region];
		}
		return region;
	});
	convertIfPresent(annotation, opts, "serviceAccountEmail", "serviceAccount", serviceAccountFromShorthand);
	convertIfPresent(annotation, opts, "timeout", "timeoutSeconds", durationFromSeconds);
	convertIfPresent(annotation, opts, "failurePolicy", "retry", (retry) => {
		return retry ? { retry: true } : null;
	});
	return annotation;
}
/**
* Apply GlobalOptions to endpoint manifest.
* @internal
*/
function optionsToEndpoint(opts) {
	const endpoint = {};
	copyIfPresent(endpoint, opts, "omit", "concurrency", "minInstances", "maxInstances", "ingressSettings", "labels", "timeoutSeconds", "cpu");
	convertIfPresent(endpoint, opts, "serviceAccountEmail", "serviceAccount");
	if (opts.vpcConnector !== undefined) {
		if (opts.vpcConnector === null || opts.vpcConnector instanceof ResetValue) {
			endpoint.vpc = RESET_VALUE;
		} else {
			const vpc = { connector: opts.vpcConnector };
			convertIfPresent(vpc, opts, "egressSettings", "vpcConnectorEgressSettings");
			endpoint.vpc = vpc;
		}
	}
	convertIfPresent(endpoint, opts, "availableMemoryMb", "memory", (mem) => {
		return typeof mem === "object" ? mem : MemoryOptionToMB[mem];
	});
	convertIfPresent(endpoint, opts, "region", "region", (region) => {
		if (typeof region === "string") {
			return [region];
		}
		return region;
	});
	convertIfPresent(endpoint, opts, "secretEnvironmentVariables", "secrets", (secrets) => secrets.map((secret) => ({ key: typeof secret === "string" ? secret : secret.name })));
	return endpoint;
}
/**
* @hidden
* @alpha
*/
function __getSpec() {
	return {
		globalOptions: getGlobalOptions(),
		params: declaredParams.map((p) => p.toSpec())
	};
}

//#endregion
export { RESET_VALUE, __getSpec, getGlobalOptions, optionsToEndpoint, optionsToTriggerAnnotations, setGlobalOptions };