const require_common_trace = require('../common/trace.js');

//#region src/v2/trace.ts
function wrapTraceContext(handler) {
	return (...args) => {
		let traceParent;
		if (args.length === 1) {
			traceParent = require_common_trace.extractTraceContext(args[0]);
		} else {
			traceParent = require_common_trace.extractTraceContext(args[0].headers);
		}
		if (!traceParent) {
			return handler.apply(null, args);
		}
		return require_common_trace.traceContext.run(traceParent, handler, ...args);
	};
}

//#endregion
exports.wrapTraceContext = wrapTraceContext;