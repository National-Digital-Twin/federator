# Open-Source Compliance & Code Audit Report

**Repository:** `federator`  
**Date of Last Audit:** `2025-03-21`    
**Reviewed By:** `Kainos Software`

<!-- SPDX-License-Identifier: OGL-UK-3.0 -->

---

## Overview

As part of NDTP’s commitment to open-source compliance and security best practices, this repository has undergone
a comprehensive audit using FOSSology and Copyleaks to verify:
- All third-party components are properly licensed and attributed.
- No proprietary or restricted-license code has been included.
- No unintentional code duplication from external sources.
- All code follows NDTP’s dual-license model (Apache 2.0 for code, OGL-UK-3.0 for documentation).
-------------------------------------------------------------------------------------------------

## Tools Used for the Audit

| Tool | Purpose | Scan Date    |
|------|---------|--------------|
| FOSSology | Open-source license compliance scanner | `2025-03-21` |
| Copyleaks | AI-driven plagiarism and duplicate code detection | `2025-03-21` |
| Manual Review | Compliance team manually reviewed flagged files | `2025-03-21` |
----------------------------------------------------------------------------------

## License Compliance Check (FOSSology)

Issues Identified:
- FSSology scans identified potential GPL2.0 license matches but a manual review confirmed these to be false positives
- Action Taken: None required

All required attributions have been added to [NOTICE.md](./NOTICE.md).

---

## Duplicate Code and Attribution Check (Copyleaks)

|         Scanned Files          | Plagiarism Risk Detected? |                                                              Source Match                                                              |                               Resolution                                |
|--------------------------------|---------------------------|----------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| `CustomClientInterceptor.java` | Yes                       | [Source](https://stackoverflow.com/questions/47155084/intercepting-logging-requests-and-responses-in-grpc)                             | Matched parts of code in a StackOverflow answer, pretty simple counters |
| `ExecutorShutdownTask.java`    | Yes                       | [Source](https://stackoverflow.com/questions/73480206/is-there-a-way-to-schedule-a-task-until-an-event-occurs-or-a-specific-amount-of) | Matched parts of code in a StackOverflow answer, pretty simple counters |
| `create-kafka-topics.sh`       | Yes                       | [Source](https://stackoverflow.com/questions/58004386/i-need-to-create-a-kafka-image-with-topics-already-created)                      | Matched parts of code in a StackOverflow answer                         |
| `AccessMap.java`               | Yes                       | [Source](https://stackoverflow.com/questions/332079/in-java-how-do-i-convert-a-byte-array-to-a-string-of-hex-digits-while-keeping-l)   | Matched parts of code in a StackOverflow answer                         |

Issues Identified and Resolutions:
- None required  
- All unintentional code reuse has been resolved or attributed correctly.

---

## Final Compliance Status

After running FOSSology,  and Copyleaks scans, this repository is:

- Fully compliant
- Necessary actions taken / Further review required

Next Steps:
- None Required

Maintained by the National Digital Twin Programme.
