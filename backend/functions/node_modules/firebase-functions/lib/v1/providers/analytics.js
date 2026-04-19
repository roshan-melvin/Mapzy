const require_rolldown_runtime = require('../../_virtual/rolldown_runtime.js');
const require_v1_cloud_functions = require('../cloud-functions.js');

//#region src/v1/providers/analytics.ts
var analytics_exports = /* @__PURE__ */ require_rolldown_runtime.__export({
	AnalyticsEvent: () => AnalyticsEvent,
	AnalyticsEventBuilder: () => AnalyticsEventBuilder,
	ExportBundleInfo: () => ExportBundleInfo,
	UserDimensions: () => UserDimensions,
	UserPropertyValue: () => UserPropertyValue,
	_eventWithOptions: () => _eventWithOptions,
	event: () => event,
	provider: () => provider,
	service: () => service
});
/** @internal */
const provider = "google.analytics";
/** @internal */
const service = "app-measurement.com";
/**
* Registers a function to handle analytics events.
*
* @param analyticsEventType Name of the analytics event type to which
*   this Cloud Function is scoped.
*
* @returns Analytics event builder interface.
*/
function event(analyticsEventType) {
	return _eventWithOptions(analyticsEventType, {});
}
/** @internal */
function _eventWithOptions(analyticsEventType, options) {
	return new AnalyticsEventBuilder(() => {
		if (!process.env.GCLOUD_PROJECT) {
			throw new Error("process.env.GCLOUD_PROJECT is not set.");
		}
		return "projects/" + process.env.GCLOUD_PROJECT + "/events/" + analyticsEventType;
	}, options);
}
/**
* The Firebase Analytics event builder interface.
*
* Access via `functions.analytics.event()`.
*/
var AnalyticsEventBuilder = class {
	/** @hidden */
	constructor(triggerResource, options) {
		this.triggerResource = triggerResource;
		this.options = options;
	}
	/**
	* Event handler that fires every time a Firebase Analytics event occurs.
	*
	* @param handler Event handler that fires every time a Firebase Analytics event
	*   occurs.
	*
	* @returns A function that you can export and deploy.
	*/
	onLog(handler) {
		const dataConstructor = (raw) => {
			return new AnalyticsEvent(raw.data);
		};
		return require_v1_cloud_functions.makeCloudFunction({
			handler,
			provider,
			eventType: "event.log",
			service,
			legacyEventType: `providers/google.firebase.analytics/eventTypes/event.log`,
			triggerResource: this.triggerResource,
			dataConstructor,
			options: this.options
		});
	}
};
/** Interface representing a Firebase Analytics event that was logged for a specific user. */
var AnalyticsEvent = class {
	/** @hidden */
	constructor(wireFormat) {
		this.params = {};
		if (wireFormat.eventDim && wireFormat.eventDim.length > 0) {
			const eventDim = wireFormat.eventDim[0];
			copyField(eventDim, this, "name");
			copyField(eventDim, this, "params", (p) => mapKeys(p, unwrapValue));
			copyFieldTo(eventDim, this, "valueInUsd", "valueInUSD");
			copyFieldTo(eventDim, this, "date", "reportingDate");
			copyTimestampToString(eventDim, this, "timestampMicros", "logTime");
			copyTimestampToString(eventDim, this, "previousTimestampMicros", "previousLogTime");
		}
		copyFieldTo(wireFormat, this, "userDim", "user", (dim) => new UserDimensions(dim));
	}
};
function isValidUserProperty(property) {
	if (property == null || typeof property !== "object" || !("value" in property)) {
		return false;
	}
	const { value } = property;
	if (value == null) {
		return false;
	}
	if (typeof value === "object" && Object.keys(value).length === 0) {
		return false;
	}
	return true;
}
/**
* Interface representing the user who triggered the events.
*/
var UserDimensions = class {
	/** @hidden */
	constructor(wireFormat) {
		copyFields(wireFormat, this, [
			"userId",
			"deviceInfo",
			"geoInfo",
			"appInfo"
		]);
		copyTimestampToString(wireFormat, this, "firstOpenTimestampMicros", "firstOpenTime");
		this.userProperties = {};
		copyField(wireFormat, this, "userProperties", (r) => {
			const entries = Object.entries(r).filter(([, v]) => isValidUserProperty(v)).map(([k, v]) => [k, new UserPropertyValue(v)]);
			return Object.fromEntries(entries);
		});
		copyField(wireFormat, this, "bundleInfo", (r) => new ExportBundleInfo(r));
		if (!this.userId && this.userProperties["user_id"]) {
			this.userId = this.userProperties["user_id"].value;
		}
	}
};
/** Predefined or custom properties stored on the client side. */
var UserPropertyValue = class {
	/** @hidden */
	constructor(wireFormat) {
		copyField(wireFormat, this, "value", unwrapValueAsString);
		copyTimestampToString(wireFormat, this, "setTimestampUsec", "setTime");
	}
};
/** Interface representing the bundle these events were uploaded to. */
var ExportBundleInfo = class {
	/** @hidden */
	constructor(wireFormat) {
		copyField(wireFormat, this, "bundleSequenceId");
		copyTimestampToMillis(wireFormat, this, "serverTimestampOffsetMicros", "serverTimestampOffset");
	}
};
/** @hidden */
function copyFieldTo(from, to, fromField, toField, transform) {
	if (typeof from[fromField] === "undefined") {
		return;
	}
	if (transform) {
		to[toField] = transform(from[fromField]);
		return;
	}
	to[toField] = from[fromField];
}
/** @hidden */
function copyField(from, to, field, transform = (from$1) => from$1) {
	copyFieldTo(from, to, field, field, transform);
}
/** @hidden */
function copyFields(from, to, fields) {
	for (const field of fields) {
		copyField(from, to, field);
	}
}
function mapKeys(obj, transform) {
	const entries = Object.entries(obj).map(([k, v]) => [k, transform(v)]);
	return Object.fromEntries(entries);
}
/** @hidden */
function unwrapValueAsString(wrapped) {
	if (!wrapped || typeof wrapped !== "object") {
		return "";
	}
	const keys = Object.keys(wrapped);
	if (keys.length === 0) {
		return "";
	}
	const key = keys[0];
	const value = wrapped[key];
	if (value === null || value === undefined) {
		return "";
	}
	return value.toString();
}
/** @hidden */
const xValueNumberFields = [
	"intValue",
	"floatValue",
	"doubleValue"
];
/** @hidden */
function unwrapValue(wrapped) {
	const key = Object.keys(wrapped)[0];
	const value = unwrapValueAsString(wrapped);
	return xValueNumberFields.includes(key) ? Number(value) : value;
}
/** @hidden */
function copyTimestampToMillis(from, to, fromName, toName) {
	if (from[fromName] !== undefined) {
		to[toName] = Math.round(from[fromName] / 1e3);
	}
}
/** @hidden */
function copyTimestampToString(from, to, fromName, toName) {
	if (from[fromName] !== undefined) {
		to[toName] = new Date(from[fromName] / 1e3).toISOString();
	}
}

//#endregion
exports.AnalyticsEvent = AnalyticsEvent;
exports.AnalyticsEventBuilder = AnalyticsEventBuilder;
exports.ExportBundleInfo = ExportBundleInfo;
exports.UserDimensions = UserDimensions;
exports.UserPropertyValue = UserPropertyValue;
exports._eventWithOptions = _eventWithOptions;
Object.defineProperty(exports, 'analytics_exports', {
  enumerable: true,
  get: function () {
    return analytics_exports;
  }
});
exports.event = event;
exports.provider = provider;
exports.service = service;