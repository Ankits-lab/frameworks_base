#
# Original audio package that shipped on G1
#
# This file is included from core.mk so that all devices will have these sounds
#
# TODO: Clean up for future releases
#

LOCAL_PATH:= frameworks/base/data/sounds

PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/F1_MissedCall.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/F1_MissedCall.ogg \
	$(LOCAL_PATH)/F1_New_MMS.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/F1_New_MMS.ogg \
	$(LOCAL_PATH)/F1_New_SMS.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/F1_New_SMS.ogg \
	$(LOCAL_PATH)/Alarm_Buzzer.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Alarm_Buzzer.ogg \
	$(LOCAL_PATH)/Alarm_Beep_01.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Alarm_Beep_01.ogg \
	$(LOCAL_PATH)/Alarm_Beep_02.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Alarm_Beep_02.ogg \
	$(LOCAL_PATH)/Alarm_Classic.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Alarm_Classic.ogg \
	$(LOCAL_PATH)/Alarm_Beep_03.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Alarm_Beep_03.ogg \
	$(LOCAL_PATH)/Alarm_Rooster_02.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/alarms/Alarm_Rooster_02.ogg \
	$(LOCAL_PATH)/Ring_Classic_02.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Ring_Classic_02.ogg \
	$(LOCAL_PATH)/Ring_Digital_02.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Ring_Digital_02.ogg \
	$(LOCAL_PATH)/Ring_Synth_04.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Ring_Synth_04.ogg \
	$(LOCAL_PATH)/Ring_Synth_02.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Ring_Synth_02.ogg \
	$(LOCAL_PATH)/notifications/Beat_Box_Android.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Beat_Box_Android.ogg \
	$(LOCAL_PATH)/notifications/Heaven.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Heaven.ogg \
	$(LOCAL_PATH)/notifications/TaDa.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/TaDa.ogg \
	$(LOCAL_PATH)/notifications/Tinkerbell.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Tinkerbell.ogg \
	$(LOCAL_PATH)/effects/Effect_Tick.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/Effect_Tick.ogg \
	$(LOCAL_PATH)/effects/KeypressStandard.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/KeypressStandard.ogg \
	$(LOCAL_PATH)/effects/KeypressSpacebar.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/KeypressSpacebar.ogg \
	$(LOCAL_PATH)/effects/KeypressDelete.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/KeypressDelete.ogg \
	$(LOCAL_PATH)/effects/KeypressReturn.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/KeypressReturn.ogg \
	$(LOCAL_PATH)/effects/VideoRecord.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/VideoRecord.ogg \
	$(LOCAL_PATH)/effects/VideoStop.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/VideoStop.ogg \
	$(LOCAL_PATH)/effects/camera_click.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ui/camera_click.ogg \
	$(LOCAL_PATH)/newwavelabs/BeatPlucker.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/BeatPlucker.ogg \
	$(LOCAL_PATH)/newwavelabs/CaffeineSnake.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/CaffeineSnake.ogg

ifneq ($(MINIMAL_NEWWAVELABS),true)
PRODUCT_COPY_FILES += \
	$(LOCAL_PATH)/newwavelabs/BentleyDubs.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/BentleyDubs.ogg \
	$(LOCAL_PATH)/newwavelabs/BirdLoop.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/BirdLoop.ogg \
	$(LOCAL_PATH)/newwavelabs/CaribbeanIce.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/CaribbeanIce.ogg \
	$(LOCAL_PATH)/newwavelabs/CurveBall.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/CurveBall.ogg \
	$(LOCAL_PATH)/newwavelabs/EtherShake.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/EtherShake.ogg \
	$(LOCAL_PATH)/newwavelabs/FriendlyGhost.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/FriendlyGhost.ogg \
	$(LOCAL_PATH)/newwavelabs/GameOverGuitar.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/GameOverGuitar.ogg \
	$(LOCAL_PATH)/newwavelabs/Growl.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Growl.ogg \
	$(LOCAL_PATH)/newwavelabs/InsertCoin.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/InsertCoin.ogg \
	$(LOCAL_PATH)/newwavelabs/LoopyLounge.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/LoopyLounge.ogg \
	$(LOCAL_PATH)/newwavelabs/LoveFlute.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/LoveFlute.ogg \
	$(LOCAL_PATH)/newwavelabs/MidEvilJaunt.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/MidEvilJaunt.ogg \
	$(LOCAL_PATH)/newwavelabs/MildlyAlarming.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/MildlyAlarming.ogg \
	$(LOCAL_PATH)/newwavelabs/NewPlayer.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/NewPlayer.ogg \
	$(LOCAL_PATH)/newwavelabs/Noises1.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Noises1.ogg \
	$(LOCAL_PATH)/newwavelabs/Noises2.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Noises2.ogg \
	$(LOCAL_PATH)/newwavelabs/Noises3.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Noises3.ogg \
	$(LOCAL_PATH)/newwavelabs/OrganDub.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/OrganDub.ogg \
	$(LOCAL_PATH)/newwavelabs/RomancingTheTone.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/RomancingTheTone.ogg \
	$(LOCAL_PATH)/newwavelabs/SitarVsSitar.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/SitarVsSitar.ogg \
	$(LOCAL_PATH)/newwavelabs/SpringyJalopy.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/SpringyJalopy.ogg \
	$(LOCAL_PATH)/newwavelabs/Terminated.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/Terminated.ogg \
	$(LOCAL_PATH)/newwavelabs/TwirlAway.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/TwirlAway.ogg \
	$(LOCAL_PATH)/newwavelabs/VeryAlarmed.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/VeryAlarmed.ogg \
	$(LOCAL_PATH)/newwavelabs/World.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/World.ogg \
	$(LOCAL_PATH)/newwavelabs/DearDeer.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/DearDeer.ogg \
	$(LOCAL_PATH)/newwavelabs/DontPanic.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/DontPanic.ogg \
	$(LOCAL_PATH)/newwavelabs/Highwire.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Highwire.ogg \
	$(LOCAL_PATH)/newwavelabs/KzurbSonar.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/KzurbSonar.ogg \
	$(LOCAL_PATH)/newwavelabs/OnTheHunt.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/OnTheHunt.ogg \
	$(LOCAL_PATH)/newwavelabs/Voila.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/notifications/Voila.ogg \
	$(LOCAL_PATH)/newwavelabs/CrazyDream.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/CrazyDream.ogg \
	$(LOCAL_PATH)/newwavelabs/DreamTheme.ogg:$(TARGET_COPY_OUT_PRODUCT)/media/audio/ringtones/DreamTheme.ogg
endif
