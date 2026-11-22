package com.easymomo.app.ussd.model

import com.easymomo.app.domain.model.NetworkType
import com.easymomo.app.domain.model.TransactionType

enum class PresetFlowGroup {
    TELECEL_AGENT,
    ONE4ALL,
    STANDALONE,
    BUS_WITHDRAWAL
}

enum class CustomUssdFeatureKey(
    val key: String,
    val displayName: String,
    val defaultHint: String
) {
    TELECEL_AGENT("TELECEL_AGENT", "Telecel Agent", "Auto (Telecel SIM)"),
    ONE4ALL("ONE4ALL", "One4All", "Auto (Active SIM)"),
    EVD_BALANCE("EVD_BALANCE", "EVD Balance", "Auto (MTN SIM)"),
    EVD_COMMISSION("EVD_COMMISSION", "EVD Commission", "Auto (MTN SIM)"),
    MOMO_STATEMENT("MOMO_STATEMENT", "MoMo Statement", "Auto (MTN SIM)"),
    BUS_WITHDRAWAL("BUS_WITHDRAWAL", "Bus Withdrawal", "Auto (Telecel SIM)")
}

data class PresetUssdFlow(
    val id: String,
    val title: String,
    val subtitle: String,
    val group: PresetFlowGroup,
    val featureKey: CustomUssdFeatureKey,
    val network: NetworkType,
    val transactionType: TransactionType,
    val requiresPhone: Boolean,
    val requiresAmount: Boolean,
    val requiresCodeOrTill: Boolean = false,
    val profile: UssdFlowProfile
)

object PresetUssdFlows {

    private fun menuStep(
        id: String,
        option: String,
        expectedPrompts: List<String>,
        nextStepId: String?
    ) = UssdFlowStep(
        id = id,
        type = StepType.MATCH_PROMPT,
        expectedPrompts = (listOf("$option.", "$option)", "$option:") + expectedPrompts).distinct(),
        matchMode = PromptMatchMode.NORMALIZED_TEXT,
        action = StepAction.SEND_MENU_OPTION,
        responseType = ResponseType.STATIC_OPTION,
        menuOption = option,
        nextStepId = nextStepId
    )

    private fun phoneStep(
        id: String,
        nextStepId: String?,
        optional: Boolean = false,
        skipToStepId: String? = null
    ) = UssdFlowStep(
        id = id,
        type = StepType.MATCH_PROMPT,
        expectedPrompts = if (optional) {
            listOf("re-enter", "reenter", "repeat", "confirm", "again", "retype", "verify", "number", "phone")
        } else {
            listOf("number", "phone", "mobile", "recipient", "subscriber", "customer", "msisdn", "enter")
        },
        matchMode = PromptMatchMode.NORMALIZED_TEXT,
        action = StepAction.SEND_VARIABLE,
        responseType = ResponseType.TRANSACTION_CUSTOMER_PHONE,
        variable = "customer_phone",
        nextStepId = nextStepId,
        optional = optional,
        skipToStepId = skipToStepId
    )

    private fun codeOrTillStep(
        id: String,
        nextStepId: String?
    ) = UssdFlowStep(
        id = id,
        type = StepType.MATCH_PROMPT,
        expectedPrompts = listOf("agent", "code", "till", "merchant", "number", "id", "enter"),
        matchMode = PromptMatchMode.NORMALIZED_TEXT,
        action = StepAction.SEND_VARIABLE,
        variable = "merchant_code",
        nextStepId = nextStepId
    )

    private fun amountStep(
        id: String,
        nextStepId: String?
    ) = UssdFlowStep(
        id = id,
        type = StepType.MATCH_PROMPT,
        expectedPrompts = listOf("amount", "ghs", "cedis", "value", "enter"),
        matchMode = PromptMatchMode.NORMALIZED_TEXT,
        action = StepAction.SEND_VARIABLE,
        responseType = ResponseType.TRANSACTION_AMOUNT,
        variable = "amount",
        nextStepId = nextStepId
    )

    private fun operationalIdStep(
        id: String = "ENTER_OPERATIONAL_ID",
        nextStepId: String? = "AUTHORIZE_PIN"
    ) = UssdFlowStep(
        id = id,
        type = StepType.MATCH_PROMPT,
        expectedPrompts = listOf(
            "operational id", "operator id", "operation id", "agent id", "operational", "operator"
        ),
        matchMode = PromptMatchMode.NORMALIZED_TEXT,
        action = StepAction.PAUSE_FOR_OPERATIONAL_ID,
        responseType = ResponseType.MANUAL_OPERATIONAL_ID,
        nextStepId = nextStepId
    )

    private fun optionalPinStep(
        id: String = "AUTHORIZE_PIN",
        nextStepId: String = "FINAL_RESULT"
    ) = UssdFlowStep(
        id = id,
        type = StepType.REQUIRE_AUTHORIZATION,
        expectedPrompts = listOf("enter pin", "momo pin", "mm pin", "secret code", "pin"),
        matchMode = PromptMatchMode.NORMALIZED_TEXT,
        action = StepAction.PAUSE_FOR_PIN,
        responseType = ResponseType.MANUAL_SENSITIVE_INPUT,
        nextStepId = nextStepId,
        optional = true,
        skipToStepId = nextStepId
    )

    private fun terminalResultStep(
        id: String = "FINAL_RESULT",
        successPrompts: List<String> = listOf("success", "completed", "confirmed", "approved", "balance", "statement", "commission")
    ) = UssdFlowStep(
        id = id,
        type = StepType.MATCH_RESULT,
        responseType = ResponseType.TERMINAL_RESULT,
        successPrompts = successPrompts,
        failurePrompts = listOf("failed", "invalid", "insufficient", "error", "declined")
    )

    private fun telecelAgentFlow(
        id: String,
        title: String,
        subtitle: String,
        thirdOption: String
    ) = PresetUssdFlow(
        id = id,
        title = title,
        subtitle = subtitle,
        group = PresetFlowGroup.TELECEL_AGENT,
        featureKey = CustomUssdFeatureKey.TELECEL_AGENT,
        network = NetworkType.TELECEL,
        transactionType = TransactionType.CUSTOM_USSD,
        requiresPhone = false,
        requiresAmount = true,
        profile = UssdFlowProfile(
            flowId = id,
            network = NetworkType.TELECEL.code,
            transactionType = TransactionType.CUSTOM_USSD.code,
            version = 1,
            entryCode = "*110#",
            timeoutSeconds = 90,
            enabled = true,
            verifiedWithSim = true,
            configStatus = "PRESET",
            steps = listOf(
                UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_2A"),
                menuStep("STEP_2A", "2", listOf("agent", "float", "business", "services", "select"), "STEP_2B"),
                menuStep("STEP_2B", "2", listOf("float", "comm", "bus", "transfer", "select"), "STEP_3"),
                menuStep("STEP_3", thirdOption, listOf("float", "comm", "bus", "select"), "ENTER_AMOUNT"),
                amountStep("ENTER_AMOUNT", "ENTER_OPERATIONAL_ID"),
                operationalIdStep("ENTER_OPERATIONAL_ID", "AUTHORIZE_PIN"),
                optionalPinStep(),
                terminalResultStep()
            )
        )
    )

    val TELECEL_AGENT_FLOWS: List<PresetUssdFlow> = listOf(
        telecelAgentFlow(
            id = "TELECEL_AGENT_COMM_TO_FLOAT",
            title = "Commission to Float",
            subtitle = "*110# → 2 → 2 → 3 → Amount → Operational ID",
            thirdOption = "3"
        ),
        telecelAgentFlow(
            id = "TELECEL_AGENT_FLOAT_TO_BUS",
            title = "Float to Business",
            subtitle = "*110# → 2 → 2 → 2 → Amount → Operational ID",
            thirdOption = "2"
        ),
        telecelAgentFlow(
            id = "TELECEL_AGENT_BUS_TO_FLOAT",
            title = "Business to Float",
            subtitle = "*110# → 2 → 2 → 1 → Amount → Operational ID",
            thirdOption = "1"
        )
    )

    val ONE4ALL_FLOWS: List<PresetUssdFlow> = listOf(
        PresetUssdFlow(
            id = "ONE4ALL_AIRTIME",
            title = "Airtime",
            subtitle = "*161*1# → 1 → Phone → Phone → Amount",
            group = PresetFlowGroup.ONE4ALL,
            featureKey = CustomUssdFeatureKey.ONE4ALL,
            network = NetworkType.UNKNOWN,
            transactionType = TransactionType.AIRTIME,
            requiresPhone = true,
            requiresAmount = true,
            profile = UssdFlowProfile(
                flowId = "ONE4ALL_AIRTIME",
                network = "ANY",
                transactionType = TransactionType.AIRTIME.code,
                version = 1,
                entryCode = "*161*1#",
                timeoutSeconds = 90,
                enabled = true,
                verifiedWithSim = true,
                configStatus = "PRESET",
                steps = listOf(
                    UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_1"),
                    menuStep("STEP_1", "1", listOf("airtime", "recharge", "sell", "select"), "ENTER_CUSTOMER_NUMBER"),
                    phoneStep("ENTER_CUSTOMER_NUMBER", "CONFIRM_CUSTOMER_NUMBER"),
                    phoneStep("CONFIRM_CUSTOMER_NUMBER", "ENTER_AMOUNT", optional = true, skipToStepId = "ENTER_AMOUNT"),
                    amountStep("ENTER_AMOUNT", "AUTHORIZE_PIN"),
                    optionalPinStep(),
                    terminalResultStep()
                )
            )
        ),
        PresetUssdFlow(
            id = "ONE4ALL_TOPUP",
            title = "Topup",
            subtitle = "*161*1# → 4 → 1 → 1 → Amount",
            group = PresetFlowGroup.ONE4ALL,
            featureKey = CustomUssdFeatureKey.ONE4ALL,
            network = NetworkType.UNKNOWN,
            transactionType = TransactionType.CUSTOM_USSD,
            requiresPhone = false,
            requiresAmount = true,
            profile = UssdFlowProfile(
                flowId = "ONE4ALL_TOPUP",
                network = "ANY",
                transactionType = TransactionType.CUSTOM_USSD.code,
                version = 1,
                entryCode = "*161*1#",
                timeoutSeconds = 90,
                enabled = true,
                verifiedWithSim = true,
                configStatus = "PRESET",
                steps = listOf(
                    UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_4"),
                    menuStep("STEP_4", "4", listOf("topup", "top-up", "wallet", "account", "select"), "STEP_1A"),
                    menuStep("STEP_1A", "1", listOf("topup", "momo", "mobile", "select"), "STEP_1B"),
                    menuStep("STEP_1B", "1", listOf("momo", "wallet", "confirm", "select"), "ENTER_AMOUNT"),
                    amountStep("ENTER_AMOUNT", "AUTHORIZE_PIN"),
                    optionalPinStep(),
                    terminalResultStep()
                )
            )
        ),
        PresetUssdFlow(
            id = "ONE4ALL_BALANCE",
            title = "Balance",
            subtitle = "*161*1# → 3 → 1",
            group = PresetFlowGroup.ONE4ALL,
            featureKey = CustomUssdFeatureKey.ONE4ALL,
            network = NetworkType.UNKNOWN,
            transactionType = TransactionType.BALANCE,
            requiresPhone = false,
            requiresAmount = false,
            profile = UssdFlowProfile(
                flowId = "ONE4ALL_BALANCE",
                network = "ANY",
                transactionType = TransactionType.BALANCE.code,
                version = 1,
                entryCode = "*161*1#",
                timeoutSeconds = 60,
                enabled = true,
                verifiedWithSim = true,
                configStatus = "PRESET",
                steps = listOf(
                    UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_3"),
                    menuStep("STEP_3", "3", listOf("account", "balance", "report", "select"), "STEP_1"),
                    menuStep("STEP_1", "1", listOf("balance", "check", "select"), "AUTHORIZE_PIN"),
                    optionalPinStep(),
                    terminalResultStep()
                )
            )
        ),
        PresetUssdFlow(
            id = "ONE4ALL_STATEMENT",
            title = "Statement",
            subtitle = "*161*1# → 3 → 2",
            group = PresetFlowGroup.ONE4ALL,
            featureKey = CustomUssdFeatureKey.ONE4ALL,
            network = NetworkType.UNKNOWN,
            transactionType = TransactionType.STATEMENT,
            requiresPhone = false,
            requiresAmount = false,
            profile = UssdFlowProfile(
                flowId = "ONE4ALL_STATEMENT",
                network = "ANY",
                transactionType = TransactionType.STATEMENT.code,
                version = 1,
                entryCode = "*161*1#",
                timeoutSeconds = 60,
                enabled = true,
                verifiedWithSim = true,
                configStatus = "PRESET",
                steps = listOf(
                    UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_3"),
                    menuStep("STEP_3", "3", listOf("account", "statement", "report", "select"), "STEP_2"),
                    menuStep("STEP_2", "2", listOf("statement", "mini", "history", "select"), "AUTHORIZE_PIN"),
                    optionalPinStep(),
                    terminalResultStep()
                )
            )
        )
    )

    val EVD_BALANCE = PresetUssdFlow(
        id = "MTN_EVD_BALANCE",
        title = "EVD Balance",
        subtitle = "*171# → 3 → 1",
        group = PresetFlowGroup.STANDALONE,
        featureKey = CustomUssdFeatureKey.EVD_BALANCE,
        network = NetworkType.MTN,
        transactionType = TransactionType.BALANCE,
        requiresPhone = false,
        requiresAmount = false,
        profile = UssdFlowProfile(
            flowId = "MTN_EVD_BALANCE",
            network = NetworkType.MTN.code,
            transactionType = TransactionType.BALANCE.code,
            version = 1,
            entryCode = "*171#",
            timeoutSeconds = 60,
            enabled = true,
            verifiedWithSim = true,
            configStatus = "PRESET",
            steps = listOf(
                UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_3"),
                menuStep("STEP_3", "3", listOf("evd", "cash in", "balance", "select"), "STEP_1"),
                menuStep("STEP_1", "1", listOf("balance", "check", "select"), "AUTHORIZE_PIN"),
                optionalPinStep(),
                terminalResultStep()
            )
        )
    )

    val EVD_COMMISSION = PresetUssdFlow(
        id = "MTN_EVD_COMMISSION",
        title = "EVD Commission",
        subtitle = "*171# → 6",
        group = PresetFlowGroup.STANDALONE,
        featureKey = CustomUssdFeatureKey.EVD_COMMISSION,
        network = NetworkType.MTN,
        transactionType = TransactionType.COMMISSION,
        requiresPhone = false,
        requiresAmount = false,
        profile = UssdFlowProfile(
            flowId = "MTN_EVD_COMMISSION",
            network = NetworkType.MTN.code,
            transactionType = TransactionType.COMMISSION.code,
            version = 1,
            entryCode = "*171#",
            timeoutSeconds = 60,
            enabled = true,
            verifiedWithSim = true,
            configStatus = "PRESET",
            steps = listOf(
                UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_6"),
                menuStep("STEP_6", "6", listOf("commission", "evd", "select"), "AUTHORIZE_PIN"),
                optionalPinStep(),
                terminalResultStep()
            )
        )
    )

    val MOMO_STATEMENT = PresetUssdFlow(
        id = "MTN_MOMO_STATEMENT",
        title = "MoMo Statement",
        subtitle = "*171# → 7 → 4 → 1",
        group = PresetFlowGroup.STANDALONE,
        featureKey = CustomUssdFeatureKey.MOMO_STATEMENT,
        network = NetworkType.MTN,
        transactionType = TransactionType.STATEMENT,
        requiresPhone = false,
        requiresAmount = false,
        profile = UssdFlowProfile(
            flowId = "MTN_MOMO_STATEMENT",
            network = NetworkType.MTN.code,
            transactionType = TransactionType.STATEMENT.code,
            version = 1,
            entryCode = "*171#",
            timeoutSeconds = 60,
            enabled = true,
            verifiedWithSim = true,
            configStatus = "PRESET",
            steps = listOf(
                UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_7"),
                menuStep("STEP_7", "7", listOf("my wallet", "wallet", "select"), "STEP_4"),
                menuStep("STEP_4", "4", listOf("statement", "mini", "select"), "STEP_1"),
                menuStep("STEP_1", "1", listOf("statement", "mini", "select"), "AUTHORIZE_PIN"),
                optionalPinStep(),
                terminalResultStep()
            )
        )
    )

    val BUS_WITHDRAWAL_AGENT_CODE = PresetUssdFlow(
        id = "BUS_WITHDRAWAL_AGENT_CODE",
        title = "Agent Code",
        subtitle = "*110# → 2 → 4 → 2 → Agent Code → Amount → Operational ID",
        group = PresetFlowGroup.BUS_WITHDRAWAL,
        featureKey = CustomUssdFeatureKey.BUS_WITHDRAWAL,
        network = NetworkType.TELECEL,
        transactionType = TransactionType.CASH_OUT,
        requiresPhone = false,
        requiresAmount = true,
        requiresCodeOrTill = true,
        profile = UssdFlowProfile(
            flowId = "BUS_WITHDRAWAL_AGENT_CODE",
            network = NetworkType.TELECEL.code,
            transactionType = TransactionType.CASH_OUT.code,
            version = 1,
            entryCode = "*110#",
            timeoutSeconds = 90,
            enabled = true,
            verifiedWithSim = true,
            configStatus = "PRESET",
            steps = listOf(
                UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_2"),
                menuStep("STEP_2", "2", listOf("agent", "business", "withdraw", "select"), "STEP_4"),
                menuStep("STEP_4", "4", listOf("withdraw", "bus", "cash", "select"), "STEP_2_SUB"),
                menuStep("STEP_2_SUB", "2", listOf("agent", "code", "select"), "ENTER_CODE"),
                codeOrTillStep("ENTER_CODE", "ENTER_AMOUNT"),
                amountStep("ENTER_AMOUNT", "ENTER_OPERATIONAL_ID"),
                operationalIdStep("ENTER_OPERATIONAL_ID", "AUTHORIZE_PIN"),
                optionalPinStep(),
                terminalResultStep()
            )
        )
    )

    val BUS_WITHDRAWAL_TILL_NUMBER = PresetUssdFlow(
        id = "BUS_WITHDRAWAL_TILL_NUMBER",
        title = "Till Number",
        subtitle = "*110# → 2 → 4 → 1 → Till Number → Amount → Operational ID",
        group = PresetFlowGroup.BUS_WITHDRAWAL,
        featureKey = CustomUssdFeatureKey.BUS_WITHDRAWAL,
        network = NetworkType.TELECEL,
        transactionType = TransactionType.CASH_OUT,
        requiresPhone = false,
        requiresAmount = true,
        requiresCodeOrTill = true,
        profile = UssdFlowProfile(
            flowId = "BUS_WITHDRAWAL_TILL_NUMBER",
            network = NetworkType.TELECEL.code,
            transactionType = TransactionType.CASH_OUT.code,
            version = 1,
            entryCode = "*110#",
            timeoutSeconds = 90,
            enabled = true,
            verifiedWithSim = true,
            configStatus = "PRESET",
            steps = listOf(
                UssdFlowStep("START", StepType.INITIATE_USSD, nextStepId = "STEP_2"),
                menuStep("STEP_2", "2", listOf("agent", "business", "withdraw", "select"), "STEP_4"),
                menuStep("STEP_4", "4", listOf("withdraw", "bus", "cash", "select"), "STEP_1_SUB"),
                menuStep("STEP_1_SUB", "1", listOf("till", "number", "select"), "ENTER_CODE"),
                codeOrTillStep("ENTER_CODE", "ENTER_AMOUNT"),
                amountStep("ENTER_AMOUNT", "ENTER_OPERATIONAL_ID"),
                operationalIdStep("ENTER_OPERATIONAL_ID", "AUTHORIZE_PIN"),
                optionalPinStep(),
                terminalResultStep()
            )
        )
    )
}
