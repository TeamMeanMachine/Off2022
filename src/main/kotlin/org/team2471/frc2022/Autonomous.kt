package org.team2471.frc2022

import edu.wpi.first.networktables.EntryListenerFlags
import edu.wpi.first.networktables.NetworkTableInstance
import edu.wpi.first.wpilibj.DriverStation
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard
import org.team2471.frc.lib.control.PDConstantFController
import org.team2471.frc.lib.coroutines.delay
import org.team2471.frc.lib.coroutines.parallel
import org.team2471.frc.lib.framework.use
import org.team2471.frc.lib.motion.following.driveAlongPath
import org.team2471.frc.lib.motion_profiling.Autonomi
import org.team2471.frc.lib.units.degrees
import org.team2471.frc.lib.util.Timer
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

//        addOption("8 Foot Straight Downfield", "8 Foot Straight Downfield")
//        addOption("8 Foot Straight Upfield", "8 Foot Straight Upfield")
//        addOption("8 Foot Straight Sidefield", "8 Foot Straight Sidefield")
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
        addOption("Straight Back Shoot Auto", "straightBackShootAuto")




    }

    init {
//        DriverStation.reportWarning("Starting auto init warning", false)
//        DriverStation.reportError("Starting auto init error", false)         //            trying to get individual message in event log to get timestamp -- untested

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
        println("Got into Auto fun autonomous. Hi. 888888888888888 ${Robot.recentTimeTaken()}")
        val selAuto = SmartDashboard.getString("Autos/selected", "no auto selected")
        SmartDashboard.putString("autoStatus", "init")
        println("Selected Auto = *****************   $selAuto ****************************  ${Robot.recentTimeTaken()}")
        when (selAuto) {
            "Tests" -> testAuto()
            "Carpet Bias Test" -> carpetBiasTest()
            "Right Side 5 Auto" -> right5v3()
            "Straight Back Shoot Auto" -> straightBackShootAuto()
            else -> println("No function found for ---->$selAuto<-----  ${Robot.recentTimeTaken()}")
        }
        SmartDashboard.putString("autoStatus", "complete")
        println("finished autonomous  ${Robot.recentTimeTaken()}")
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
    suspend fun right5() = use(Drive, Shooter, Intake, Feeder) {
        Limelight.backLedEnabled = true
        val auto = autonomi["NewAuto"]
        if (auto != null) {
            autoShoot()
            parallel({
                Intake.changeAngle(Intake.PIVOT_INTAKE)
                Intake.setIntakePower(Intake.INTAKE_POWER)
             }, {
                Drive.driveAlongPath(auto ["4 - 1st Ball"], true)
            })
            delay(0.5)
            Drive.driveAlongPath(auto["5 - 2nd Ball"], false)
            autoShoot()
            Drive.driveAlongPath(auto["2 - Grab balls"], false)
            delay(0.5)
            Drive.driveAlongPath(auto["3 - Move"], false)
            autoShoot()
        }
    }
    suspend fun right5v3() = use(Drive, Shooter, Intake, Feeder) {
        val t = Timer()
        t.start()
        Limelight.backLedEnabled = true
        val auto = autonomi["NewAuto"]
        if (auto != null) {
            val firstAuto = auto["4 - 1st Ball"]
            Drive.position = firstAuto.getPosition(0.0)
            Drive.heading = firstAuto.headingCurve.getValue(0.0).degrees
            println("auto started - shooting first ball from ${Drive.position} angle: ${Drive.heading}")
            parallel({
                autoShootv2(1, 3.0)
            }, {
                powerSave()
                Feeder.autoFeedMode = true
            })

            println("lowering intake and getting 1st ball")
            parallel({
                intake()
            }, {
                Drive.driveAlongPath(firstAuto, false)
            })
//            println("delaying 0.5")
//            delay(0.5)
            println("getting 2nd ball")
            Drive.driveAlongPath(auto["5 - 2nd Ball"], false)
            println("shooting 2nd batch")
            parallel({
                delay(0.25)
                Intake.setIntakePower(0.0)
                     },
                {
                    autoShootv2(2, 2.5)
                }
            )
            println("getting 3rd batch")
            Intake.setIntakePower(Intake.INTAKE_POWER)
            Drive.driveAlongPath(auto["6 - Grab balls"], false)
            delay(0.5)
            println("current auto time is ${t.get()}")
            // go for close shot if we have at least 1.5 seconds left to shoot after running path
            if (t.get() > 15.0 - (1.5 + auto["7.2 - Move Extra Close"].duration)) {
                Drive.driveAlongPath(auto["7.1 - Move"], false)
            } else {
                println("Going for close shot!!")
                Drive.driveAlongPath(auto["7.2 - Move Extra Close"], false)
            }
            println("shooting 3rd batch")
            parallel({
                delay(0.25)
                Intake.setIntakePower(0.0)
            },
                {
                    autoShootv2(2, 2.5)
                }
            )
            Drive.aimPDController = PDConstantFController (0.011, 0.032, 0.008)
            println("auto complete in ${t.get()} seconds")
            Feeder.autoFeedMode = false
        }
    }

//    suspend fun right5() = use(Intake, Shooter, Feeder, Drive) {
//        println("In right5 auto.")
//        val auto = autonomi["Right Side 5 Auto"]
//        if (auto != null) {
//            Limelight.backLedEnabled = true
//            parallel({
//                Intake.changeAngle(Intake.PIVOT_INTAKE)
//                Intake.setIntakePower(Intake.INTAKE_POWER)
//            }, {
//                Drive.driveAlongPath(auto["1- First Field Cargo"], true)
//            })
//            delay(1.0)
//            Shooter.rpmOffset = 300.0
//            autoShoot()
//            Drive.driveAlongPath(auto["2- Field Cargo"], false)
//            autoShoot()
//            Drive.driveAlongPath(auto["3- Feeder Cargo"])
//            delay(0.5)
////            parallel({
////                delay(0.5)
////                Intake.setIntakePower(0.0)
////            }, {
//                Drive.driveAlongPath(auto["5- Short Shoot"], false)
////            })
//            Shooter.rpmOffset = 200.0
//            autoShoot()
//        }
//    }


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
    suspend fun right5v2() = use(Intake, Shooter, Feeder, Drive) {
        println("In right5 auto.")
        val auto = autonomi["Right Side 5 Auto"]
        if (auto != null) {
            Limelight.backLedEnabled = true
            Feeder.autoFeedMode = true
            parallel({
                Intake.changeAngle(Intake.PIVOT_INTAKE)
                Intake.setIntakePower(Intake.INTAKE_POWER)
            }, {
                Drive.driveAlongPath(auto["1- First Field Cargo"], true)
            })
            delay(0.5)
            Intake.setIntakePower(0.0)
            autoShootv2(2, 2.5)
            Intake.setIntakePower(Intake.INTAKE_POWER)
            Drive.driveAlongPath(auto["2- Field Cargo"], false)
            Intake.setIntakePower(0.0)
            autoShootv2(2, 2.5)
            Intake.setIntakePower(Intake.INTAKE_POWER)
            Drive.driveAlongPath(auto["3- Feeder Cargo"])
            delay(0.5)
            Drive.driveAlongPath(auto["5- Short Shoot"], false)
            Intake.setIntakePower(0.0)
            autoShootv2(2, 4.0)
            Feeder.autoFeedMode = false
        }
    }

    suspend fun straightBackShootAuto() = use(Intake, Shooter, Feeder, Drive) {
        println("In straightBackShoot auto.")
        val auto = autonomi["Straight Back Shoot Auto"]
        if (auto != null) {
            Limelight.backLedEnabled = true
            parallel({
                Intake.changeAngle(Intake.PIVOT_INTAKE)
                Intake.setIntakePower(Intake.INTAKE_POWER)
            }, {
                Drive.driveAlongPath(auto["1- First Field Cargo"], true)
            })
            delay(1.0)
            Shooter.rpmOffset = 300.0
            autoShoot()
        }
    }
//
//    suspend fun leftSideAuto() = use(Drive, Shooter, Intake) {
//        val auto = autonomi["Left Side 2 Auto"]
//        if (auto != null) {
//            parallel({
//                Intake.setExtend(true)
//                Intake.setIntakePower(Intake.INTAKE_POWER)
//            }, {
//                Drive.driveAlongPath(auto["1- Get Cargo"], true)
//            })
//            shoot()
//            parallel({
//                Intake.setExtend(false)
//                Intake.setIntakePower(0.0)
//            }, {
//                parallel({
//                    Intake.setExtend(true)
//                    Intake.setIntakePower(Intake.INTAKE_POWER)
//                }, {
//                    Drive.driveAlongPath(auto["2- Pick up ball"], false)
//                })
//                Drive.driveAlongPath(auto["3- Pick up ball2"], false)
//            })
//            Drive.driveAlongPath(auto["4- Dump balls"], false)
//            spit()
//        }
//
//        suspend fun middle4() = use(Drive, Shooter, Intake) {
//            val auto = autonomi["Middle 4 Ball"]
//            if (auto != null) {
//                parallel({
//                    Intake.setExtend(true)
//                    Intake.setIntakePower(Intake.INTAKE_POWER)
//                }, {
//                    Drive.driveAlongPath(auto["[01- Get Ball]"], true)
//                })
//                shoot()
//                parallel({
//                    Intake.setExtend(false)
//                    Intake.setIntakePower(0.0)
//                }, {
//                    Drive.driveAlongPath(auto["[02- Grab Ball]"], false)
//                })
//                Drive.driveAlongPath(auto["[03- Shoot]"], false)
//                shoot()
//            }
//        }
    //}
}