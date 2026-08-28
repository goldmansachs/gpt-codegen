package org.rj.modelgen.bpmn.generation;

import java.util.List;
import java.util.regex.Pattern;

public class BpmnConstants {
    public static class NodeTypes {
        public static class Comparable {
            public static final String TASK = "task";
            public static final String USER_TASK = "usertask";
            public static final String SERVICE_TASK = "servicetask";
            public static final String SCRIPT_TASK = "scripttask";
            public static final String BUSINESS_RULE_TASK = "businessruletask";
        }

        public static final String TASK = "task";
        public static final String TASK_USER = "user";
        public static final String TASK_USER_TASK = "userTask";
        public static final String TASK_SERVICE = "service";
        public static final String TASK_SERVICE_TASK = "serviceTask";
        public static final String TASK_SCRIPT = "script";
        public static final String TASK_SCRIPT_TASK = "scriptTask";
        public static final String TASK_BUSINESS_RULE = "businessRule";
        public static final String TASK_BUSINESS_RULE_TASK = "businessRuleTask";
        public static final String TASK_MANUAL = "manual";
        public static final String TASK_MANUAL_TASK = "manualTask";
        public static final String TASK_RECEIVE = "receive";
        public static final String TASK_RECEIVE_TASK = "receiveTask";
        public static final String TASK_SEND = "send";
        public static final String TASK_SEND_TASK = "sendTask";
        public static final String TASK_CALL = "call";
        public static final String TASK_CALL_TASK = "callTask";
        public static final String TASK_CALL_ACTIVITY_TASK = "callActivity";

        public static final String GATEWAY_EXCLUSIVE = "exclusiveGateway";
        public static final String GATEWAY_INCLUSIVE = "inclusiveGateway";
        public static final String GATEWAY_PARALLEL = "parallelGateway";
        public static final String GATEWAY_EVENT = "eventBasedGateway";
        public static final String GATEWAY_SUFFIX = "Gateway";

        public static final String START_EVENT = "startEvent";
        public static final String END_EVENT = "endEvent";

        public static final String SEQUENCE_FLOW = "sequenceFlow";

        public static final String PROCESS_CONFIG = "processConfig";

        public static final String BOUNDARY_EVENT = "boundaryEvent";
        public static final String TIMER_BOUNDARY_EVENT = "timerBoundaryEvent";
        public static final String MESSAGE_BOUNDARY_EVENT = "messageBoundaryEvent";
        public static final String ERROR_BOUNDARY_EVENT = "errorBoundaryEvent";
        public static final String CONDITIONAL_BOUNDARY_EVENT = "conditionalBoundaryEvent";
        public static final String TERMINATE_END_EVENT = "terminateEndEvent";
        public static final String ERROR_END_EVENT = "errorEndEvent";
        public static final String MESSAGE_END_EVENT = "messageEndEvent";
        public static final String MESSAGE_START_EVENT = "messageStartEvent";
        public static final String TIMER_START_EVENT = "timerStartEvent";
        public static final String ERROR_START_EVENT = "errorStartEvent";
        public static final String CONDITIONAL_START_EVENT = "conditionalStartEvent";
        public static final String MESSAGE_INTERMEDIATE_CATCH_EVENT = "messageIntermediateCatchEvent";
        public static final String MESSAGE_INTERMEDIATE_THROW_EVENT = "messageIntermediateThrowEvent";
        public static final String TIMER_INTERMEDIATE_CATCH_EVENT = "timerIntermediateCatchEvent";
        public static final String CONDITIONAL_INTERMEDIATE_CATCH_EVENT = "conditionalIntermediateCatchEvent";

        public static final List<String> BOUNDARY_EVENT_TYPES = List.of(TIMER_BOUNDARY_EVENT, MESSAGE_BOUNDARY_EVENT, ERROR_BOUNDARY_EVENT, CONDITIONAL_BOUNDARY_EVENT);

        public static final List<String> START_EVENT_TYPES = List.of(START_EVENT, MESSAGE_START_EVENT, TIMER_START_EVENT, ERROR_START_EVENT, CONDITIONAL_START_EVENT);

        public static final List<String> END_EVENT_TYPES = List.of(END_EVENT, TERMINATE_END_EVENT, MESSAGE_END_EVENT, ERROR_END_EVENT);

        public static boolean isStartEventType(String elementType) {
            return START_EVENT_TYPES.contains(elementType);
        }

        public static boolean isEndEventType(String elementType) {
            return END_EVENT_TYPES.contains(elementType);
        }
    }

    public static class Patterns {
        // Matches getVariable('varName') or ${getVariable("varName")}
        public static final Pattern VAR_READ_PATTERN = Pattern.compile("(?:\\$\\{)?getVariable\\s*\\(\\s*['\"]([a-zA-Z_][a-zA-Z0-9_.]*)['\"]\\s*\\)(?:\\})?");
        // Matches setVariable('varName', value, 'varType')
        public static final Pattern VAR_WRITE_PATTERN = Pattern.compile("setVariable\\s*\\(\\s*['\"]?([a-zA-Z_][a-zA-Z0-9_.]*)['\"]?\\s*,\\s*(.+?)\\s*,\\s*['\"]([a-zA-Z_][a-zA-Z0-9_]*)['\"]\\s*\\)");
        // Matches throw('errorMessage')
        public static final Pattern THROW_ERROR_PATTERN = Pattern.compile("throw\\s*\\(\\s*(.+?)\\s*\\)");
        // Matches getGlobalVariable('varName', [arg1, arg2])
        public static final Pattern GLOBAL_VAR_READ_PATTERN = Pattern.compile("getGlobalVariable\\s*\\(\\s*['\"]([^'\"]+)['\"](?:\\s*,\\s*\\[([^\\]]*)\\])?\\s*\\)");

        public static final Pattern VAR_INTERPOLATED_PAYLOAD_READ_PATTERN = Pattern.compile("\\$\\{payload\\.([^}]+)}");
        public static final Pattern VAR_PAYLOAD_READ_PATTERN = Pattern.compile("payload\\.([a-zA-Z0-9_.]+)");
        public static final Pattern VAR_PAYLOAD_WRITE_PATTERN = Pattern.compile("payload\\.([a-zA-Z0-9_]+)\\s*(?<![!=<>])=(?!=)\\s*([^;]+)");
    }

    public static class Namespaces {
        public static final String DEFAULT_NAMESPACE = "default";
        public static final String DEFAULT_NAMESPACE_URI = "http://default.com/example";
    }

    public static class CommonTaskConstants {
        public static final String OUTPUT_VAR_ATTR = "outputVariable";
        public static final String RESPONSE_VAR = "response";
        public static final String HEADER_INPUT = "headers";
        public static final String HEADER_NAME = "headerName";
        public static final String HEADER_EXPRESSION = "headerExpression";
        public static final String QUERY_PARAM_INPUT = "queryParams";
        public static final String PARAM_NAME = "parameterName";
        public static final String PARAM_EXPRESSION = "parameterExpression";
        public static final String EXTENSION_ELEMENTS = "extensionElements";
        public static final String ATTR_NODE_DESCRIPTION = "nodeDescription";
        public static final String ATTR_NODE_NAME= "name";
        public static final String ATTR_NODE_ID = "id";
        public static final String ID_ATTR = "elementId";
    }

    public static class ExecutionListenerConstants {
        public static final String ENTRY_SCRIPT_INPUT = "entryScript";
        public static final String EXIT_SCRIPT_INPUT = "exitScript";
        public static final String EVENT_START = "start";
        public static final String EVENT_END = "end";
        public static final String EXECUTION_LISTENER_ELEMENT = "executionListener";
        public static final String SCRIPT_ELEMENT = "script";
        public static final String EVENT_ATTR = "event";
        public static final String SCRIPT_FORMAT_ATTR = "scriptFormat";
    }

    public static class UserTaskConstants {
        public static final List<String> ATTRIBUTES = List.of("taskName", "taskDescription", "sourceSystemName", "taskPriority", "taskDeadline", "maximumActiveTasks");
    }

    public static class ServiceTaskConstants {
        public static final List<String> ATTRIBUTES = List.of("serviceUrl", "uriPath", "httpMethod", "inputExpression", "outputVariable");
        public static final List<String> EXTENSIONS = List.of("inputScript", "outputScript", "queryParams", "headers");
    }

    public static class SendTaskConstants {
        public static final List<String> ATTRIBUTES = List.of("serviceUrl", "uriPath", "httpMethod", "inputExpression", "outputVariable");
        public static final List<String> EXTENSIONS = List.of("inputScript", "outputScript", "queryParams", "headers");
    }

    public static class ScriptTaskConstants {
        public static final String SCRIPT = "script";
        public static final String SCRIPT_FORMAT_GROOVY = "groovy";
        public static final String IS_PROVIDED = "isProvided";
    }

    public static class ReceiveTaskConstants {
        public static final String MESSAGE_ID_INPUT_NAME = "messageId";
        public static final String MESSAGE_PREFIX = "Message_";
        public static final String AVAILABILITY_INPUT_NAME = "availabilityRuleScript";
        public static final String AVAILABILITY_ATTR = "availability";
        public static final String SCRIPT_NOT_CONFIGURED = "// not configured";
    }

    public static class BusinessRuleTaskConstants {
        public static final List<String> ATTRIBUTES = List.of("executionEngine", "applicationId", "ruleName", "ruleVersion", "factTypesExpression", "metadataExpression");
        public static final List<String> EXTENSIONS = List.of("factTypesScript", "metadataScript", "outputScript");
    }

    public static class CallActivityTaskConstants {
        public static final String CALLED_ELEMENT = "calledElement";
        public static final String INPUT_MAPPING = "inputMapping";
        public static final String OUTPUT_MAPPING = "outputMapping";
        public static final String CAMUNDA_IN_ELEMENT = "in";
        public static final String CAMUNDA_OUT_ELEMENT = "out";
        public static final String SOURCE = "source";
        public static final String SOURCE_EXPRESSION = "sourceExpression";
        public static final String TARGET = "target";
    }

    public static class ProcessConfigConstants {
        public static final List<String> ATTRIBUTES = List.of("processId", "processName", "processDescription", "sourceSystemName", "processTypeName", "processPriority", "processDeadline", "maximumActiveProcesses");
        public static final String PROCESS_ID = "processId";
        public static final String PROCESS_NAME = "processName";
        public static final String PROCESS_NAME_ATTR = "workItemTypeDisplayName";
        public static final String ATTR_NOT_CONFIGURED = "not_configured";
        public static final String WORKFLOW_ACTION_DETAILS = "workflowActionDetails";
    }

    public static class GatewayConstants {
        public static final String DEFAULT = "default";
        public static final String CONDITIONS = "conditions";
        public static final String TARGET_NODE_ID = "targetNodeId";
        public static final String CONDITION_EXPRESSION = "conditionExpression";
    }

    public static class SubProcessConfigConstants {
        public static final List<String> ATTRIBUTES = List.of("subProcessId", "subProcessName", "subProcessDescription");
        public static final String SUBPROCESS = "subProcess";
        public static final String SUBPROCESS_CALL_NODE = "subprocessCallNode";
        public static final String SUBPROCESS_ID = "subProcessId";
        public static final String SUBPROCESS_NAME = "subProcessName";
        public static final String SUBPROCESS_DESCRIPTION = "subProcessDescription";
        public static final String TRIGGERED_BY_EVENT = "triggeredByEvent";
        public static final String EXPECTED_INLINE_SUBPROCESS_IDS = "expectedInlineSubprocessIds";
    }

    public static class EventConstants {
        public static final String IS_INTERRUPTING = "isInterrupting";
        public static final String MESSAGE_REF = "messageRef";
        public static final String ERROR_REF = "errorRef";
        public static final String ERROR_CODE = "errorCode";
        public static final String ERROR_MESSAGE = "errorMessage";
        public static final String ERROR_PREFIX = "Error_";
        public static final String TIMER_DURATION = "timeDuration";
        public static final String TIMER_DATE = "timeDate";
        public static final String TIMER_CYCLE = "timerCycle";
        public static final String CONDITION_EXPRESSION = "conditionExpression";
    }

    public static class MultiInstanceConstants {
        public static final String MULTI_INSTANCE_COLLECTION = "collection";
        public static final String MULTI_INSTANCE_ELEMENT_VARIABLE = "elementVariable";
        public static final String MULTI_INSTANCE_DISPLAY_LABEL = "displayLabel";
        public static final String MULTI_INSTANCE_COMPLETION_CONDITION = "completionCondition";
    }

    public static class Validation {
        public static final String FULL_PROCESS = "full_process";
    }

}
