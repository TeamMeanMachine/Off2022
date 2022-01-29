package org.team2471.frc2022

import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DigitalInput
import io.github.pseudoresonance.pixy2api.Pixy2
import io.github.pseudoresonance.pixy2api.Pixy2CCC

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.team2471.frc.lib.actuators.FalconID
import org.team2471.frc.lib.actuators.MotorController
import org.team2471.frc.lib.coroutines.MeanlibDispatcher
import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.periodic
import org.team2471.frc.lib.framework.Subsystem
import java.util.*


object Intake : Subsystem("Intake") {
    //val pixycam=Pixy2.createInstance(Pixy2.LinkType.SPI)
    val intakeMotor = MotorController(FalconID(Falcons.INTAKE))
    val intakePivotMotor = MotorController(FalconID(Falcons.INTAKE_PIVOT))

    private val table = NetworkTableInstance.getDefault().getTable(Intake.name)
    val currentEntry = table.getEntry("Current")

    val INTAKE_POWER = 0.75
    val INTAKE_PIVOT_POWER = 0.4

    val button = DigitalInput(9)
    var blue = 0



    init {
//        pixycam.init()
//        //pixycam.setLED(Color.red)
//        pixycam.setLamp(0.toByte(), 0.toByte()) // Turns the LEDs on


        GlobalScope.launch(MeanlibDispatcher) {
            periodic {
                currentEntry.setDouble(intakeMotor.current)
//                val blockCount: Int = pixycam.ccc.getBlocks(true, 1, 4)
//                val pixyBlocks = largestBlock()?.width
//                //println(" ${pixyBlocks} ${blockCount}")
            }
        }
    }
//    fun largestBlock() : Pixy2CCC.Block? {
//        val blocks: ArrayList<Pixy2CCC.Block>? = pixycam.ccc.blockCache // Gets a list of all blocks found by the Pixy2
//
//        var largestBlock: Pixy2CCC.Block? = null
//        if (blocks != null) {
//            for (block in blocks) { // Loops through all blocks and finds the widest one
//                if (largestBlock == null) {
//                    largestBlock = block
//                } else if (block.getWidth() > largestBlock.getWidth()) {
//                    largestBlock = block
//                }
//            }
//        }
//        return largestBlock
//    }

    val ballIsStaged: Boolean
        get() = !button.get()


    fun setIntakePower(power: Double) {
        intakeMotor.setPercentOutput(power)
    }

    fun setIntakePivotPower(power: Double) {
        intakePivotMotor.setPercentOutput(power)
    }

    suspend fun extendIntake(isOut: Boolean) {
        if (isOut) {
            setIntakePivotPower(INTAKE_PIVOT_POWER)
        } else {
            setIntakePivotPower(-INTAKE_PIVOT_POWER)
        }
        delay(1.0)
        setIntakePivotPower(0.0)
    }

    override suspend fun default() {
        if (ballIsStaged) {
            setIntakePower(INTAKE_POWER)
        } else {
            setIntakePower(0.5 * INTAKE_POWER)
        }
    }
}