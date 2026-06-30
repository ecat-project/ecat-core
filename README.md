# ECAT Core

> **Industrial Data Automation Control System - Core Framework**

ECAT Core is the core framework of the ECAT project, providing a ready-to-use automation control foundation for the industrial monitoring industry. Through a plugin architecture, it supports rapid integration of multi-vendor devices, enabling end-to-end capabilities from hardware data collection, data cleaning, and statistical analysis to automated process control and AI decision-making.

---

## Project Overview

### What is ECAT Project?

ECAT (EcoAutomation) is an industrial-grade system focused on data automation process control in the monitoring industry. Through a plugin-based development model, it enables users to quickly match their data automation control tasks, supporting the following core capabilities:

- **Hardware Data Collection** - Unified access to multi-protocol, multi-vendor devices
- **Data Cleaning & Processing** - Built-in data validation, transformation, and cleaning mechanisms
- **Statistical Analysis** - Rich data statistics and analysis tools
- **Automated Process Control** - Flexible rule engines and task scheduling
- **AI Decision Support** - Extensible intelligent algorithm integration

### ECAT Core Positioning

ECAT Core is the foundational framework of the entire ECAT project, providing:

| Capability | Description |
|------------|-------------|
| **Device Management** | Unified device abstraction layer with registration, lifecycle management, and status monitoring |
| **State Management** | Strongly-typed immutable state (`AttrState<T>`) carrying the engineering value, status, business unit, and `Class<T>` value type — single source of truth consumed by the bus, API, and persistence |
| **Integration Framework** | Plugin architecture supporting dynamic loading and hot-swapping |
| **Event Bus** | Publish-subscribe event system for loose coupling between modules |
| **Internationalization** | Complete i18n support for multi-language switching |
| **Task Scheduling** | Built-in task manager for scheduled tasks and async execution |
| **Dependency Resolution** | Intelligent version compatibility checking and dependency resolution |

### Core Features

- **Industrial-Grade Control System** - Ready-to-use solution designed specifically for environmental monitoring industry
- **Open Source Ecosystem** - Rapid integration of multi-vendor devices through collaborative open-source ecosystem
- **Compliance & Security Audit Ready** - Built-in compliance checks to meet security audit requirements
- **Continuous Intelligence Evolution** - Continuous improvement in intelligent and data-driven capabilities

---

## Core Architecture

### System Components

```
┌─────────────────────────────────────────────────────────────┐
│                        ECAT Core                            │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Integration │  │   Device    │  │    State    │         │
│  │   Manager   │  │  Registry   │  │   Manager   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │     Bus     │  │     Task    │  │     I18N    │         │
│  │  Registry   │  │   Manager   │  │  Registry   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│                    Integration Plugins                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │  Modbus  │ │ Serial   │ │   HTTP   │ │  Custom  │ ...  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### Core Modules

| Module | Description |
|--------|-------------|
| `IntegrationManager` | Integration module manager responsible for plugin loading, initialization, and lifecycle management |
| `DeviceRegistry` | Device registry managing all device instances |
| `StateManager` | State manager managing all attributes and state changes |
| `BusRegistry` | Event bus registry managing publish-subscribe events |
| `TaskManager` | Task manager providing scheduled tasks and async execution |
| `I18nRegistry` | Internationalization registry for multi-language resource management |

---

## Quick Start

### Requirements

- **Java**: JDK 8 or higher
- **Maven**: 3.6 or higher
- **OS**: Linux / Windows / macOS

### Build Project

```bash
# Clone repository
git clone https://github.com/ecat-project/ecat-core.git
cd ecat-core

# Build
mvn clean package

# Run
java -jar target/ecat-core-1.0.0.jar
```

### Maven Dependency

Add ECAT Core dependency to your plugin project:

```xml
<dependency>
    <groupId>com.ecat</groupId>
    <artifactId>ecat-core</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

---

## Plugin Development

For detailed plugin development guide, please refer to: [**ECAT Integration Development Tutorial**](./doc/tutorial/README.md)

### Documentation

- [ECAT Integration Development Tutorial](./doc/tutorial/README.md) - Complete development documentation, including:
  - Project structure and core file descriptions
  - Device development standards and naming conventions
  - I18n internationalization system details
  - Unit testing requirements
  - Maven configuration specifications
  - FAQ and best practices

---

## Core Concepts

### Device

Devices are the basic units for data collection and control in ECAT. Each device:

- Extends `DeviceBase`
- Has a unique `id` and `name`
- Contains multiple attributes (`Attribute`)
- Supports lifecycle management (`init`, `start`, `stop`, `release`)

### Attribute

Attributes are data points of a device, which can be:

| Type | Description |
|------|-------------|
| `NumericAttribute` | Numeric attributes (temperature, pressure, etc.) |
| `TextAttribute` | Text attributes |
| `BinaryAttribute` | Boolean attributes |
| `CommandAttribute` | Command attributes (executable operations) |
| `SelectAttribute` | Enumeration attributes |

#### Value model and immutable state

Each attribute holds a mutable engineering value internally and exposes it **only** through an immutable, strongly-typed snapshot:

- **`getState(): AttrState<T>`** — the single external entry point for the live state. `AttrState<T>` is immutable (`final` fields, defensive copy of collection values), so bus subscribers and the API read a self-consistent snapshot free of torn reads.
- **`AttrState<T>`** carries the engineering value (`T value`), `valueType` (`Class<T>` = the attribute's target type, not an enum), `status`, the business unit (`nativeUnit`), a derived `displayValue`, and `Instant`-based `lastUpdated`/`lastChanged`. `getDisplayValue(toUnit)` is a pure unit conversion over the already-engineering value, with no live-attribute dependency.
- **Linear-conversion attributes** (e.g. 4-20 mA → engineering quantity) keep the raw signal as an internal `rawValue` (unit `inputUnit`) that never enters state and is never persisted. Devices feed raw signals via `updateRawValue(signal)`; logic devices / the API set the engineering value via `updateValue(engineeringValue)` — the two public entry points have separated responsibilities and must not be mixed.
- **Raw-signal fields are `protected`**: `value`, `status`, `getValueType()`, and `getUpdateTime()` are not part of the external surface. Cross-module and cross-thread consumers read `getState()` instead — this is the project's invariant that no dirty/torn attribute reads reach the bus or API.
- **Persistence centers on state**: `StateManager.saveState` serializes the current `AttrState` via `PersistedState.from(state)`; `restore` rebuilds `lastState` from it. `rawValue` is not restored (re-collected on restart).

### Integration

Integrations are carriers of functional modules:

- `IntegrationBase` - Base class without device management
- `IntegrationDeviceBase` - Base class with device management

### Event Bus

Publish-subscribe event system for inter-module communication:

```java
// Publish event
core.getBusRegistry().publish("my-topic", eventData);

// Subscribe to event
core.getBusRegistry().subscribe("my-topic", new EventSubscriber() {
    @Override
    public void onEvent(String topic, Object data) {
        // Handle event
    }
});
```

---

## Resources

- **Example Plugins**: [ecat-integrations](https://github.com/ecat-project/ecat-integrations)
- **Development Guide**: [ECAT Core Development Guide](https://github.com/ecat-project/ecat-core/wiki)
- **Issue Tracker**: [GitHub Issues](https://github.com/ecat-project/ecat-core/issues)
- **Chinese Documentation**: [README_zh.md](./README_zh.md)

---

## License

1. **Core Framework**: ECAT Core is licensed under **Apache License 2.0**
2. **Plugin Development**: Plugins based on ECAT Core must comply with Apache 2.0 license terms
3. **Copyright Retention**: If reusing ECAT Core code snippets, retain the original copyright notice

### License Information

- ECAT Core Full License: [LICENSE](./LICENSE)
- Apache 2.0 License Text: https://www.apache.org/licenses/LICENSE-2.0

---

### Acknowledgements

- Home Assistant for the design reference provided
- Spring Boot for its JAR loading mechanism

---

## Contact Us

- **QQ group**: 1056412392
- **ECAT Project**: https://github.com/ecat-project
