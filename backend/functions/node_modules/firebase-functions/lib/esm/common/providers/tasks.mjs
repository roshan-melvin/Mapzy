import { error } from "../../logger/index.mjs";
import { HttpsError, decode, isValidRequest, unsafeDecodeIdToken } from "./https.mjs";

//#region src/common/providers/tasks.ts
/** @internal */
function onDispatchHandler(handler) {
	return async (req, res) => {
		try {
			if (!isValidRequest(req)) {
				error("Invalid request, unable to process.");
				throw new HttpsError("invalid-argument", "Bad Request");
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
					throw new HttpsError("unauthenticated", "Unauthenticated");
				}
				const authToken = unsafeDecodeIdToken(token);
				context.auth = {
					uid: authToken.uid,
					token: authToken,
					rawToken: token
				};
			}
			const data = decode(req.body.data);
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
			if (!(err instanceof HttpsError)) {
				error("Unhandled error", err);
				httpErr = new HttpsError("internal", "INTERNAL");
			}
			const { status } = httpErr.httpErrorCode;
			const body = { error: httpErr.toJSON() };
			res.status(status).send(body);
		}
	};
}

//#endregion
export { onDispatchHandler };