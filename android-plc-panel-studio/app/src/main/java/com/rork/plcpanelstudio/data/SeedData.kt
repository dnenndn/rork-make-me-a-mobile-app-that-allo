package com.rork.plcpanelstudio.data

/** First-run content so the app is explorable before a real bridge is configured. */
object SeedData {

    fun initialState(): WorkspaceState {
        val now = System.currentTimeMillis()
        val lineDevice = PlcDevice(
            id = "dev-line3",
            name = "Line 3 PLC",
            host = "192.168.1.40",
            port = 502,
            simulated = true,
            status = DeviceStatus.UNKNOWN
        )
        val packagingDevice = PlcDevice(
            id = "dev-packaging",
            name = "Packaging PLC",
            host = "192.168.1.55",
            port = 502,
            simulated = false,
            status = DeviceStatus.UNKNOWN
        )

        val linePanel = Panel(
            id = "panel-line3",
            name = "Line 3 Assembly",
            description = "Main assembly line with robotic pick and place.",
            deviceId = lineDevice.id,
            updatedAtMillis = now,
            components = listOf(
                PanelComponent(
                    id = "c1",
                    kind = ComponentKind.BUTTON,
                    col = 0.16f,
                    row = 0.2f,
                    label = "Start",
                    tagAddress = "M0.3",
                    direction = IoDirection.INPUT,
                    momentary = true
                ),
                PanelComponent(
                    id = "c2",
                    kind = ComponentKind.BUTTON,
                    col = 0.5f,
                    row = 0.2f,
                    label = "Stop",
                    tagAddress = "M0.4",
                    direction = IoDirection.INPUT,
                    momentary = true
                ),
                PanelComponent(
                    id = "c3",
                    kind = ComponentKind.LAMP,
                    col = 0.84f,
                    row = 0.2f,
                    label = "Motor Run",
                    tagAddress = "Q0.1",
                    direction = IoDirection.OUTPUT
                ),
                PanelComponent(
                    id = "c4",
                    kind = ComponentKind.SELECTOR,
                    col = 0.16f,
                    row = 0.6f,
                    label = "Mode Select",
                    tagAddress = "M1.0",
                    direction = IoDirection.INPUT,
                    positions = 2
                ),
                PanelComponent(
                    id = "c5",
                    kind = ComponentKind.LAMP,
                    col = 0.5f,
                    row = 0.6f,
                    label = "Conveyor",
                    tagAddress = "Q0.2",
                    direction = IoDirection.OUTPUT
                ),
                PanelComponent(
                    id = "c6",
                    kind = ComponentKind.GAUGE,
                    col = 0.84f,
                    row = 0.6f,
                    label = "Line Speed",
                    tagAddress = "MW20",
                    direction = IoDirection.OUTPUT,
                    scaleMax = 100
                )
            )
        )

        val packagingPanel = Panel(
            id = "panel-packaging",
            name = "Packaging Cell",
            description = "Carton filling and sealing with checkweigher.",
            deviceId = packagingDevice.id,
            updatedAtMillis = now - 3_600_000L,
            components = listOf(
                PanelComponent(
                    id = "p1",
                    kind = ComponentKind.BUTTON,
                    col = 0.2f,
                    row = 0.3f,
                    label = "Cycle Start",
                    tagAddress = "I0.0",
                    direction = IoDirection.INPUT
                ),
                PanelComponent(
                    id = "p2",
                    kind = ComponentKind.LAMP,
                    col = 0.5f,
                    row = 0.3f,
                    label = "Sealer Heat",
                    tagAddress = "Q1.0",
                    direction = IoDirection.OUTPUT
                ),
                PanelComponent(
                    id = "p3",
                    kind = ComponentKind.GAUGE,
                    col = 0.8f,
                    row = 0.3f,
                    label = "Throughput",
                    tagAddress = "MW10",
                    direction = IoDirection.OUTPUT,
                    scaleMax = 100
                )
            )
        )

        return WorkspaceState(
            panels = listOf(linePanel, packagingPanel),
            devices = listOf(lineDevice, packagingDevice),
            activePanelId = linePanel.id
        )
    }
}
