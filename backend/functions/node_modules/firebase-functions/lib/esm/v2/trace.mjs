import { extractTraceContext, traceContext } from "../common/trace.mjs";

//#region src/v2/trace.ts
function wrapTraceContext(handler) {
	return (...args) => {
		let traceParent;
		if (args.length === 1) {
			traceParent = extractTraceContext(args[0]);
		} else {
			traceParent = extractTraceContext(args[0].headers);
		}
		if (!traceParent) {
			return handler.apply(null, args);
		}
		return traceContext.run(traceParent, handler, ...args);
	};
}

//#endregion
export { wrapTraceContext };