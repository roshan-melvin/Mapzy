const require_logger_index = require('../logger/index.js');
const require_params_index = require('../params/index.js');
const require_common_options = require('../common/options.js');
const require_common_encoding = require('../common/encoding.js');

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
		require_logger_index.warn("Calling setGlobalOptions twice leads to undefined behavior");
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
	require_common_encoding.copyIfPresent(annotation, opts, "concurrency", "minInstances", "maxInstances", "ingressSettings", "labels", "vpcConnector", "vpcConnectorEgressSettings", "secrets");
	require_common_encoding.convertIfPresent(annotation, opts, "availableMemoryMb", "memory", (mem) => {
		return MemoryOptionToMB[mem];
	});
	require_common_encoding.convertIfPresent(annotation, opts, "regions", "region", (region) => {
		if (typeof region === "string") {
			return [region];
		}
		return region;
	});
	require_common_encoding.convertIfPresent(annotation, opts, "serviceAccountEmail", "serviceAccount", require_common_encoding.serviceAccountFromShorthand);
	require_common_encoding.convertIfPresent(annotation, opts, "timeout", "timeoutSeconds", require_common_encoding.durationFromSeconds);
	require_common_encoding.convertIfPresent(annotation, opts, "failurePolicy", "retry", (retry) => {
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
	require_common_encoding.copyIfPresent(endpoint, opts, "omit", "concurrency", "minInstances", "maxInstances", "ingressSettings", "labels", "timeoutSeconds", "cpu");
	require_common_encoding.convertIfPresent(endpoint, opts, "serviceAccountEmail", "serviceAccount");
	if (opts.vpcConnector !== undefined) {
		if (opts.vpcConnector === null || opts.vpcConnector instanceof require_common_options.ResetValue) {
			endpoint.vpc = require_common_options.RESET_VALUE;
		} else {
			const vpc = { connector: opts.vpcConnector };
			require_common_encoding.convertIfPresent(vpc, opts, "egressSettings", "vpcConnectorEgressSettings");
			endpoint.vpc = vpc;
		}
	}
	require_common_encoding.convertIfPresent(endpoint, opts, "availableMemoryMb", "memory", (mem) => {
		return typeof mem === "object" ? mem : MemoryOptionToMB[mem];
	});
	require_common_encoding.convertIfPresent(endpoint, opts, "region", "region", (region) => {
		if (typeof region === "string") {
			return [region];
		}
		return region;
	});
	require_common_encoding.convertIfPresent(endpoint, opts, "secretEnvironmentVariables", "secrets", (secrets) => secrets.map((secret) => ({ key: typeof secret === "string" ? secret : secret.name })));
	return endpoint;
}
/**
* @hidden
* @alpha
*/
function __getSpec() {
	return {
		globalOptions: getGlobalOptions(),
		params: require_params_index.declaredParams.map((p) => p.toSpec())
	};
}

//#endregion
exports.RESET_VALUE = require_common_options.RESET_VALUE;
exports.__getSpec = __getSpec;
exports.getGlobalOptions = getGlobalOptions;
exports.optionsToEndpoint = optionsToEndpoint;
exports.optionsToTriggerAnnotations = optionsToTriggerAnnotations;
exports.setGlobalOptions = setGlobalOptions;