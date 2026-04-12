# Strategy and Factory Pattern Class Diagram

This diagram focuses on how scheduling algorithms are extended through Strategy and selected through a factory-like registry in the service layer.

```mermaid
classDiagram
direction LR

class ScheduleController {
  -SchedulingService schedulingService
  +createSchedule(request, algorithm) ResponseEntity~ScheduleResult~
}

class SchedulingService {
  <<Service>>
  -Map~String, ChargingStrategy~ strategies
  -EnergyPriceRepository energyPriceRepository
  -CO2IntensityRepository co2IntensityRepository
  -DataSyncService dataSyncService
  -ScheduleResultRepository scheduleResultRepository
  -UserRepository userRepository
  +createSchedule(request, algorithm) ScheduleResult
  +createSchedule(request, algorithm, userId) ScheduleResult
  -resolveStrategy(strategyKey) ChargingStrategy
  -normalizeStrategyKey(algorithm) String
  -toConstraints(request) UserConstraints
  -toPriceData(prices) List~GridData~
  -toHourlyCo2Data(co2Series) List~GridData~
}

class ChargingStrategy {
  <<interface>>
  +solve(constraints, priceData, co2Data) ScheduleResult
}

class NaiveChargingStrategy {
  <<Component("naive")>>
  +solve(constraints, priceData, co2Data) ScheduleResult
}

class GreedyChargingStrategy {
  <<Component("greedy")>>
  +solve(constraints, priceData, co2Data) ScheduleResult
}

class DynamicProgrammingChargingStrategy {
  <<Component("optimal")>>
  +solve(constraints, priceData, co2Data) ScheduleResult
}

class UserConstraints {
  <<record>>
  +currentSocPercent: double
  +targetSocPercent: double
  +batteryCapacityKwh: double
  +maxChargingPowerKw: double
  +plugInTime: LocalDateTime
  +departureTime: LocalDateTime
  +priceZone: String
  +weightPrice: double
  +weightCO2: double
  +energyRequiredKwh() double
}

class GridData {
  <<record>>
  +timestamp: LocalDateTime
  +value: double
}

class ScheduleRequest {
  <<record>>
  +currentSocPercent: double
  +targetSocPercent: double
  +batteryCapacityKwh: double
  +maxChargingPowerKw: double
  +plugInTime: LocalDateTime
  +departureTime: LocalDateTime
  +priceZone: String
  +weightPrice: double
  +weightCO2: double
}

class ScheduleResult {
  <<record>>
  +slots: List~ChargingSlot~
  +totalPredictedCost: double
  +totalPredictedEmissions: double
  +degradedMode: DegradedMode
}

ScheduleController --> SchedulingService : uses
SchedulingService --> ChargingStrategy : selects and executes
SchedulingService ..> UserConstraints : builds
SchedulingService ..> GridData : transforms repository data
SchedulingService ..> ScheduleRequest : input
SchedulingService ..> ScheduleResult : output

ChargingStrategy <|.. NaiveChargingStrategy : implements
ChargingStrategy <|.. GreedyChargingStrategy : implements
ChargingStrategy <|.. DynamicProgrammingChargingStrategy : implements

note for SchedulingService "Factory role:\nresolveStrategy(key) fetches from Map<String, ChargingStrategy>\ninjected by Spring from @Component bean names."
```

## OCP proof point

`SchedulingService` depends on `ChargingStrategy` abstraction, not concrete algorithms. The current codebase already has three interchangeable implementations (`naive`, `greedy`, `optimal`). A future 4th algorithm (for example, `BalancedChargingStrategy`) can be added by implementing `ChargingStrategy` and registering it as a Spring component key, without changing core scheduling flow in `createSchedule(...)`.
