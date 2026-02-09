const require_logger_index = require('../../logger/index.js');
const require_common_providers_https = require('./https.js');

//#region src/common/providers/tasks.ts
/** @internal */
function onDispatchHandler(handler) {
	return async (req, res) => {
		try {
			if (!require_common_providers_https.isValidRequest(req)) {
				require_logger_index.error("Invalid request, unable to process.");
				throw new require_common_providers_https.HttpsError("invalid-argument", "Bad Request");
			}
			const headers = {};
			for (const [key, value] of Object.entries(req.headers)) {
				if (!Array.isArray(value)) {
					headers[key] = value;
				}
			}
			const context = {
				queueName: req.header("X-CloudTasks-QueueName"),
				id: req.header("X-CloudTasks-TaskName"),
				retryCount: req.header("X-CloudTasks-TaskRetryCount") ? Number(req.header("X-CloudTasks-TaskRetryCount")) : undefined,
				executionCount: req.header("X-CloudTasks-TaskExecutionCount") ? Number(req.header("X-CloudTasks-TaskExecutionCount")) : undefined,
				scheduledTime: req.header("X-CloudTasks-TaskETA"),
				previousResponse: req.header("X-CloudTasks-TaskPreviousResponse") ? Number(req.header("X-CloudTasks-TaskPreviousResponse")) : undefined,
				retryReason: req.header("X-CloudTasks-TaskRetryReason"),
				headers
			};
			if (!process.env.FUNCTIONS_EMULATOR) {
				const authHeader = req.header("Authorization") || "";
				const token = authHeader.match(/^Bearer (.*)$/)?.[1];
				if (!token) {
					throw new require_common_providers_https.HttpsError("unauthenticated", "Unauthenticated");
				}
				const authToken = require_common_providers_https.unsafeDecodeIdToken(token);
				context.auth = {
					uid: authToken.uid,
					token: authToken,
					rawToken: token
				};
			}
			const data = require_common_providers_https.decode(req.body.data);
			if (handler.length === 2) {
				await handler(data, context);
			} else {
				const arg = {
					...context,
					data
				};
				await handler(arg);
			}
			res.status(204).end();
		} catch (err) {
			let httpErr = err;
			if (!(err instanceof require_common_providers_https.HttpsError)) {
				require_logger_index.error("Unhandled error", err);
				httpErr = new require_common_providers_https.HttpsError("internal", "INTERNAL");
			}
			const { status } = httpErr.httpErrorCode;
			const body = { error: httpErr.toJSON() };
			res.status(status).send(body);
		}
	};
}

//#endregion
exports.onDispatchHandler = onDispatchHandler;