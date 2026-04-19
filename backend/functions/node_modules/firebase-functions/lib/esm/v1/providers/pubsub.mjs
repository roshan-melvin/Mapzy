import { __export } from "../../_virtual/rolldown_runtime.mjs";
import { makeCloudFunction } from "../cloud-functions.mjs";

//#region src/v1/providers/pubsub.ts
var pubsub_exports = /* @__PURE__ */ __export({
	Message: () => Message,
	ScheduleBuilder: () => ScheduleBuilder,
	TopicBuilder: () => TopicBuilder,
	_scheduleWithOptions: () => _scheduleWithOptions,
	_topicWithOptions: () => _topicWithOptions,
	provider: () => provider,
	schedule: () => schedule,
	service: () => service,
	topic: () => topic
});
/** @internal */
const provider = "google.pubsub";
/** @internal */
const service = "pubsub.googleapis.com";
/**
* Registers a Cloud Function triggered when a Google Cloud Pub/Sub message
* is sent to a specified topic.
*
* @param topic - The Pub/Sub topic to watch for message events.
* @returns Pub/Sub topic builder interface.
*/
function topic(topic$1) {
	return _topicWithOptions(topic$1, {});
}
/** @internal */
function _topicWithOptions(topic$1, options) {
	if (topic$1.indexOf("/") !== -1) {
		throw new Error("Topic name may not have a /");
	}
	return new TopicBuilder(() => {
		if (!process.env.GCLOUD_PROJECT) {
			throw new Error("process.env.GCLOUD_PROJECT is not set.");
		}
		return `projects/${process.env.GCLOUD_PROJECT}/topics/${topic$1}`;
	}, options);
}
/**
* The Google Cloud Pub/Sub topic builder.
*
* Access via `functions.pubsub.topic()`.
*/
var TopicBuilder = class {
	/** @hidden */
	constructor(triggerResource, options) {
		this.triggerResource = triggerResource;
		this.options = options;
	}
	/**
	* Event handler that fires every time a Cloud Pub/Sub message is
	* published.
	*
	* @param handler - Event handler that runs every time a Cloud Pub/Sub message
	*   is published.
	* @returns A function that you can export and deploy.
	*/
	onPublish(handler) {
		return makeCloudFunction({
			handler,
			provider,
			service,
			triggerResource: this.triggerResource,
			eventType: "topic.publish",
			dataConstructor: (raw) => new Message(raw.data),
			options: this.options
		});
	}
};
/**
* Registers a Cloud Function to run at specified times.
*
* @param schedule - The schedule, in Unix Crontab or AppEngine syntax.
* @returns ScheduleBuilder interface.
*/
function schedule(schedule$1) {
	return _scheduleWithOptions(schedule$1, {});
}
/** @internal */
function _scheduleWithOptions(schedule$1, options) {
	const triggerResource = () => {
		if (!process.env.GCLOUD_PROJECT) {
			throw new Error("process.env.GCLOUD_PROJECT is not set.");
		}
		return `projects/${process.env.GCLOUD_PROJECT}/topics`;
	};
	return new ScheduleBuilder(triggerResource, {
		...options,
		schedule: { schedule: schedule$1 }
	});
}
/**
* The builder for scheduled functions, which are powered by
* Google Pub/Sub and Cloud Scheduler. Describes the Cloud Scheduler
* job that is deployed to trigger a scheduled function at the provided
* frequency. For more information, see
* [Schedule functions](/docs/functions/schedule-functions).
*
* Access via `functions.pubsub.schedule()`.
*/
var ScheduleBuilder = class {
	/** @hidden */
	constructor(triggerResource, options) {
		this.triggerResource = triggerResource;
		this.options = options;
	}
	retryConfig(config) {
		this.options.schedule.retryConfig = config;
		return this;
	}
	timeZone(timeZone) {
		this.options.schedule.timeZone = timeZone;
		return this;
	}
	/**
	* Event handler for scheduled functions. Triggered whenever the associated
	* scheduler job sends a Pub/Sub message.
	*
	* @param handler - Handler that fires whenever the associated
	*   scheduler job sends a Pub/Sub message.
	* @returns A function that you can export and deploy.
	*/
	onRun(handler) {
		const cloudFunction = makeCloudFunction({
			contextOnlyHandler: handler,
			provider,
			service,
			triggerResource: this.triggerResource,
			eventType: "topic.publish",
			options: this.options,
			labels: { "deployment-scheduled": "true" }
		});
		return cloudFunction;
	}
};
/**
* Interface representing a Google Cloud Pub/Sub message.
*
* @param data - Payload of a Pub/Sub message.
*/
var Message = class {
	constructor(data) {
		[this.data, this.attributes, this._json] = [
			data.data,
			data.attributes || {},
			data.json
		];
	}
	/**
	* The JSON data payload of this message object, if any.
	*/
	get json() {
		if (typeof this._json === "undefined") {
			this._json = JSON.parse(Buffer.from(this.data, "base64").toString("utf8"));
		}
		return this._json;
	}
	/**
	* Returns a JSON-serializable representation of this object.
	*
	* @returns A JSON-serializable representation of this object.
	*/
	toJSON() {
		return {
			data: this.data,
			attributes: this.attributes
		};
	}
};

//#endregion
export { Message, ScheduleBuilder, TopicBuilder, _scheduleWithOptions, _topicWithOptions, provider, pubsub_exports, schedule, service, topic };