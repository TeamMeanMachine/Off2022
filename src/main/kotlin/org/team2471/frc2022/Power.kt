package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.PowerDistribution
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem

object PowerDistribution : Subsystem("PowerDistribution") {
    private var PDH = PowerDistribution()
    private val table = NetworkTableInstance.getDefault().getTable(name)
    private val totalCurrent = table.getEntry("Current")
    init {
        GlobalScope.launch {

            println("setting power distribution info")
            val totalPower = table.getEntry("Power")
            val totalEnergy = table.getEntry("Energy")
            periodic {

                totalPower.setDouble(PDH.totalPower)
                totalEnergy.setDouble(PDH.totalEnergy)

                for (i in 0..23) {
                    try {
                        val entry = table.getEntry("port_$i")
                        entry.setDouble(PDH.getCurrent(i))
                        val x = 42
                    } catch (ex: Exception) {
                        println("port $i couldn't be read")
                    }
                }
            }
        }
    }

    override suspend fun default() {
        periodic {
            totalCurrent.setDouble(PDH.totalCurrent)
        }
    }
}