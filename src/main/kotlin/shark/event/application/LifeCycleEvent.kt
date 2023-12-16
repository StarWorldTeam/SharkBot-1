package shark.event.application

import shark.event.Event

abstract class LifeCycleEvent : Event()

class ClientSetupEvent : LifeCycleEvent()
class DiscordSetupEvent : LifeCycleEvent()
class CommonSetupEvent : LifeCycleEvent()