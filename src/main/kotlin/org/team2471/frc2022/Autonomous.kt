package org.team2471.frc2022

import edu.wpi.first.networktables.EntryListenerFlags
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DriverStation
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard
import org.team2471.frc.lib.coroutines.parallel
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.motion.following.driveAlongPath
import org.team2471.frc.lib.motion_profiling.Autonomi
import org.team2471.frc.lib.util.measureTimeFPGA
import java.io.File

private lateinit var autonomi: Autonomi


enum class Side {
    LEFT,
    RIGHT;

    operator fun not(): Side = when (this) {
        LEFT -> RIGHT
        RIGHT -> LEFT
    }
}

private var startingSide = Side.RIGHT


object AutoChooser {
    private val isRedAllianceEntry = NetworkTableInstance.getDefault().getTable("FMSInfo").getEntry("isRedAlliance")

    var cacheFile: File? = null
    var redSide: Boolean = true
        get() = isRedAllianceEntry.getBoolean(true)
        set(value) {
            field = value
            isRedAllianceEntry.setBoolean(value)
        }

    private val lyricsChooser = SendableChooser<String?>().apply {
        setDefaultOption("Country roads", "Country roads")
        addOption("take me home", "take me home")
    }

    private val testAutoChooser = SendableChooser<String?>().apply {
        addOption("None", null)
        addOption("20 Foot Test", "20 Foot Test")
        addOption("8 Foot Straight", "8 Foot Straight")
        addOption("2 Foot Circle", "2 Foot Circle")
        addOption("4 Foot Circle", "4 Foot Circle")
        addOption("8 Foot Circle", "8 Foot Circle")
        addOption("Hook Path", "Hook Path")
        setDefaultOption("90 Degree Turn", "90 Degree Turn")



    }

    private val autonomousChooser = SendableChooser<String?>().apply {
        setDefaultOption("Tests", "testAuto")
        addOption("Right Side 5 Auto", "right5")
        addOption("Middle 4 Ball", "middle4")
        addOption("Left Side 2 Auto", "leftSideAuto")




    }

    init {
//        println("Got into Autonomous' init. Hi. 222222222222222222222")
        SmartDashboard.putData("Best Song Lyrics", lyricsChooser)
        SmartDashboard.putData("Tests", testAutoChooser)
        SmartDashboard.putData("Autos", autonomousChooser)

        try {

            cacheFile = File("/home/lvuser/autonomi.json")
            if (cacheFile != null) {
                autonomi = Autonomi.fromJsonString(cacheFile?.readText())!!
                println("Autonomi cache loaded.")
            } else {
                println("Autonomi failed to load!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!! RESTART ROBOT!!!!!!")
            }
        } catch (_: Throwable) {
            DriverStation.reportError("Autonomi cache could not be found", false)
            autonomi = Autonomi()
        }
        println("In Auto Init. Before AddListener. Hi.")
        NetworkTableInstance.getDefault()
            .getTable("PathVisualizer")
            .getEntry("Autonomi").addListener({ event ->
                println("Automous change detected")
                val json = event.value.string
                if (json.isNotEmpty()) {
                    val t = measureTimeFPGA {
                        autonomi = Autonomi.fromJsonString(json) ?: Autonomi()
                    }
                    println("Loaded autonomi in $t seconds")
                    if (cacheFile != null) {
                        println("CacheFile != null. Hi.")
                        cacheFile!!.writeText(json)
                    } else {
                        println("cacheFile == null. Hi.")
                    }
                    println("New autonomi written to cache")
                } else {
                    autonomi = Autonomi()
                    DriverStation.reportWarning("Empty autonomi received from network tables", false)
                }
            }, EntryListenerFlags.kImmediate or EntryListenerFlags.kNew or EntryListenerFlags.kUpdate)
    }

    suspend fun autonomous() = use(Drive, name = "Autonomous") {
        println("Got into Auto fun autonomous. Hi. 888888888888888")
        val selAuto = SmartDashboard.getString("Autos/selected", "no auto selected")
        SmartDashboard.putString("autoStatus", "init")
        println("Selected Auto = *****************   $selAuto ****************************")
        when (selAuto) {
            "Tests" -> testAuto()
            "Carpet Bias Test" -> carpetBiasTest()
            "Right Side 5" -> right5()
            else -> println("No function found for ---->$selAuto<-----")
        }
        SmartDashboard.putString("autoStatus", "complete")
        println("finished autonomous")
    }

    private suspend fun testAuto() {
        val testPath = SmartDashboard.getString("Tests/selected", "no test selected") // testAutoChooser.selected
        if (testPath != null) {
            val testAutonomous = autonomi["Tests"]
            val path = testAutonomous?.get(testPath)
            if (path != null) {
                Drive.driveAlongPath(path, true)
            }
        }
    }


    suspend fun carpetBiasTest() = use(Drive) {
        val auto = autonomi["Carpet Bias Test"]
        if (auto != null) {
            var path = auto["01- Forward"]
            Drive.driveAlongPath(path, false)
            path = auto["02- Backward"]
            Drive.driveAlongPath(path, false)
            path = auto["03- Left"]
            Drive.driveAlongPath(path, false)
            path = auto["04- Forward"]
            Drive.driveAlongPath(path, false)
            //path = auto["05- Backward"]
        }
    }

    suspend fun test8FtStraight() = use(Drive) {
        val auto = autonomi["Tests"]
        if (auto != null) {
            val path = auto["8 Foot Straight"]
            Drive.driveAlongPath(path, true)
        }
    }

    suspend fun test8FtCircle() = use(Drive) {
        val auto = autonomi["Tests"]
        if (auto != null) {
            val path = auto["8 Foot Circle"]
            Drive.driveAlongPath(path, true)
        }
    }

    suspend fun test90DegreeTurn() = use(Drive) {
        val auto = autonomi["Tests"]
        if (auto != null) {
            Drive.driveAlongPath(auto["90 Degree Turn"], true, 2.0)
        }
    }

    suspend fun right5() = use(Drive, Shooter, Intake) {
        val auto = autonomi["Right Side 5 Auto"]
        if (auto != null) {
            shoot()
            parallel({
                Intake.extendIntake(true)
                Intake.setIntakePower(Intake.INTAKE_POWER)
            }, {
                Drive.driveAlongPath(auto["1- Field Cargo"], true)
            })
            shoot()
            Drive.driveAlongPath(auto["2- Feeder Cargo"], false)
            parallel({
                Intake.extendIntake(false)
                Intake.setIntakePower(0.0)
            }, {
                Drive.driveAlongPath(auto["3- Shoot"], false)
            })
            shoot()
        }
    }

    suspend fun leftSideAuto() = use(Drive, Shooter, Intake) {
        val auto = autonomi["Left Side 2 Auto"]
        if (auto != null) {
            parallel({
                Intake.extendIntake(true)
                Intake.setIntakePower(Intake.INTAKE_POWER)
            }, {
                Drive.driveAlongPath(auto["1- Get Cargo"], true)
            })
            shoot()
            parallel({
                Intake.extendIntake(false)
                Intake.setIntakePower(0.0)
            }, {
                parallel({
                    Intake.extendIntake(true)
                    Intake.setIntakePower(Intake.INTAKE_POWER)
                }, {
                    Drive.driveAlongPath(auto["2- Pick up ball"], false)
                })
                Drive.driveAlongPath(auto["3- Pick up ball2"], false)
            })
            Drive.driveAlongPath(auto["4- Dump balls"], false)
            spit()
        }

        suspend fun middle4() = use(Drive, Shooter, Intake) {
            val auto = autonomi["Middle 4 Ball"]
            if (auto != null) {
                parallel({
                    Intake.extendIntake(true)
                    Intake.setIntakePower(Intake.INTAKE_POWER)
                }, {
                    Drive.driveAlongPath(auto["[01- Get Ball]"], true)
                })
                shoot()
                parallel({
                    Intake.extendIntake(false)
                    Intake.setIntakePower(0.0)
                }, {
                    Drive.driveAlongPath(auto["[02- Grab Ball]"], false)
                })
                Drive.driveAlongPath(auto["[03- Shoot]"], false)
                shoot()
            }
        }
    }
}