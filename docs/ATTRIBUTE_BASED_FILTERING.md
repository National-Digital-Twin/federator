# Attribute-Based Filtering

## Overview

The Federator implements server-side attribute-based filtering to control which Kafka messages are delivered to clients. This filtering mechanism evaluates message attributes from Kafka headers against configured filter criteria to determine whether a message should be allowed through to the client.

## Implementation

The attribute-based filtering logic is implemented in the `AbstractKafkaEventMessageConductor` class, specifically in the `isEventAllowed()` method.

**Location:** `uk.gov.dbt.ndtp.federator.server.conductor.AbstractKafkaEventMessageConductor`

## Filtering Rules

The filtering mechanism operates based on three fundamental rules:

### 1. No Filter Attributes Configured

**Rule:** If no attributes are configured in the management node, all data will be passed to the client.

**Behavior:** When `filterAttributes` is null or empty, all messages are allowed through without any restriction.

```java
if (filterAttributes == null || filterAttributes.isEmpty()) {
    return true; // Allow all messages
}
```

### 2. Single Attribute Filter

**Rule:** If an attribute is set (e.g., `nationality: GBR`), only messages that have the specified attribute with the exact matching value are allowed.

**Behavior:** The system restricts data to messages that:
- Contain the specified attribute name in their headers
- Have a value that matches the configured filter value (case-insensitive comparison)

**Example:**
- Filter: `nationality=GBR`
- Result: Only messages with header containing `nationality=GBR` (or `NATIONALITY=gbr`, case-insensitive) will pass through

### 3. Multiple Attribute Filters (AND Logic)

**Rule:** If multiple attributes are configured (e.g., `organisation_type`, `nationality`, and `clearance`), the system uses AND operator logic.

**Behavior:** Messages must match ALL configured attributes to be allowed through. If any single attribute:
- Is missing from the message headers, OR
- Has a value that doesn't match the configured filter value

Then the message is filtered out.

**Example:**
- Filters: `organisation_type=NON-GOV3`, `nationality=GBR`, `clearance=0`
- Result: Only messages that have ALL three attributes with matching values will pass through

## Data Source: Kafka Headers

The filtering system reads attribute data from Kafka message headers. Specifically, it extracts the `Security-Label` header which contains attribute data in a comma-separated key=value format.

### Kafka Header Format

```json
{
  "Security-Label": "nationality=GBR,clearance=0,organisation_type=NON-GOV3",
  "Content-Type": "application/n-triples"
}
```

The `Security-Label` header is parsed to extract individual attributes:
- `nationality=GBR`
- `clearance=0`
- `organisation_type=NON-GOV3`

## Processing Flow

1. **Message Receipt:** A Kafka message is received by the conductor
2. **Header Extraction:** The `Security-Label` header is extracted from the Kafka message headers
3. **Label Parsing:** The Security-Label value is parsed into a Map of attribute name-value pairs
4. **Filter Evaluation:** Each configured filter attribute is checked against the parsed header map:
   - Attribute name must exist in the header map
   - Attribute value must match (case-insensitive)
5. **Decision:** 
   - If ALL filters match: Message is allowed and processed
   - If ANY filter fails: Message is filtered out and logged

## Technical Details

### Header Parsing

The `HeaderUtils` class provides utility methods for extracting and parsing headers:

```java
// Extract Security-Label from Kafka headers
String secLabel = getSecurityLabelFromHeaders(kafkaEvent.headers());

// Parse Security-Label into Map<String, String>
Map<String, String> headerMap = getMapFromSecurityLabel(secLabel);
```

### Attribute Matching

- **Case Handling:** Attribute names are converted to uppercase for comparison
- **Value Matching:** Values are compared case-insensitively using `equalsIgnoreCase()`
- **Missing Attributes:** If a required attribute is not present in the message headers, the message is filtered out
- **Invalid Filters:** If a filter attribute has null name or value, the message is rejected

### Logging

The system provides comprehensive logging for filtering decisions:

- **Allowed Messages:** `INFO` level logs include offset, key, and headers
- **Filtered Messages:** `WARN` level logs indicate why a message was filtered
- **Debug Information:** Detailed attribute comparison data available at `DEBUG` level

## Example Scenarios

### Scenario 1: No Filters

**Configuration:** No filter attributes set

**Message Header:**
```
Security-Label: "nationality=USA,clearance=5"
```

**Result:** ✅ Message is allowed (all messages pass when no filters configured)

---

### Scenario 2: Single Filter Match

**Configuration:** Filter attribute `nationality=GBR`

**Message Header:**
```
Security-Label: "nationality=GBR,clearance=3,organisation_type=GOV"
```

**Result:** ✅ Message is allowed (`nationality=GBR` matches)

---

### Scenario 3: Single Filter Mismatch

**Configuration:** Filter attribute `nationality=GBR`

**Message Header:**
```
Security-Label: "nationality=USA,clearance=3"
```

**Result:** ❌ Message is filtered out (`nationality` value doesn't match)

---

### Scenario 4: Multiple Filters - All Match

**Configuration:** 
- `organisation_type=NON-GOV3`
- `nationality=GBR`
- `clearance=0`

**Message Header:**
```
Security-Label: "nationality=GBR,clearance=0,organisation_type=NON-GOV3"
```

**Result:** ✅ Message is allowed (all three attributes match)

---

### Scenario 5: Multiple Filters - Partial Match

**Configuration:**
- `organisation_type=GOV`
- `nationality=GBR`
- `clearance=5`

**Message Header:**
```
Security-Label: "nationality=GBR,clearance=3,organisation_type=GOV"
```

**Result:** ❌ Message is filtered out (`clearance` value doesn't match: expected `5` but got `3`)

---

### Scenario 6: Missing Required Attribute

**Configuration:**
- `organisation_type=GOV`
- `nationality=GBR`

**Message Header:**
```
Security-Label: "clearance=5"
```

**Result:** ❌ Message is filtered out (required attributes `organisation_type` and `nationality` are missing)

## Configuration

Filter attributes are configured at the management node level and passed to the conductor during initialization. The configuration should define:

- **Attribute Name:** The key to match in the Security-Label header
- **Attribute Value:** The expected value for that attribute

The `AttributesDTO` class represents each filter attribute with `name` and `value` properties.

## Security Considerations

- This is a **server-side** filtering mechanism
- Filtering occurs before messages are sent to clients
- Filtered messages are logged but not transmitted
- The Security-Label header is the authoritative source for message attributes
- All filtering decisions are logged for audit purposes

## Performance Notes

- Filtering is performed for every message consumed from Kafka
- Header parsing and map operations are lightweight
- Attribute comparison uses case-insensitive string matching
- AND logic means filtering can short-circuit on first mismatch (fail-fast)

## Related Classes

- `AbstractKafkaEventMessageConductor`: Contains the `isEventAllowed()` filtering logic
- `HeaderUtils`: Utility methods for extracting and parsing Kafka headers
- `SecurityLabelUtil`: Parses the Security-Label format into a Map
- `AttributesDTO`: Data transfer object representing filter attributes

