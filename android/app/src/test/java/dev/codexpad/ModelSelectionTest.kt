package dev.codexpad

import dev.codexpad.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ModelSelectionTest {
    @Test fun catalogSupportsCustomEffortsAndCompatibleSwitching() {
        val model = CatalogModel.parse(JSONObject("""{"id":"catalog-id","model":"rpc-model","displayName":"Test","description":"Description","isDefault":true,"supportedReasoningEfforts":[{"reasoningEffort":"custom","description":"Custom"},{"reasoningEffort":"high","description":"High"}],"defaultReasoningEffort":"high"}"""))
        assertEquals("rpc-model", model.model)
        assertEquals("custom", model.compatibleEffort("custom"))
        assertEquals("high", model.compatibleEffort("incompatible"))
        assertEquals("custom", model.copy(defaultEffort = "invalid").compatibleEffort(null))
        assertNull(model.copy(efforts = emptyList()).compatibleEffort("high"))
        val unknown = Wire.thread(JSONObject("""{"id":"t","reasoningEffort":null}"""))
        assertNull(unknown.model)
        assertNull(unknown.reasoningEffort)
    }

    @Test fun contextUsesLastAndOnlyPositiveKnownWindows() {
        fun usage(window: String) = ContextStatus.parse(JSONObject("""{"last":{"totalTokens":120},"total":{"totalTokens":9999},"modelContextWindow":$window}"""))
        assertEquals(120L, usage("100").used)
        assertEquals(0, usage("100").remainingPercent)
        for (window in listOf("null", "0", "-1")) assertNull(usage(window).remainingPercent)
        assertNull(ContextStatus.parse(JSONObject("""{"last":{"totalTokens":12}}""")).window)
        assertNull(ContextStatus.parse(null).remainingPercent)
    }
}
