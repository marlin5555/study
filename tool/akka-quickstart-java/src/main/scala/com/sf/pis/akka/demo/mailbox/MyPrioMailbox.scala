package com.sf.pis.akka.demo.mailbox

import akka.actor.ActorSystem
import akka.dispatch.PriorityGenerator
import akka.dispatch.UnboundedPriorityMailbox
import com.sf.pis.akka.demo.pattern.Spider
import com.typesafe.config.Config

// We inherit, in this case, from UnboundedPriorityMailbox
// and seed it with the priority generator
class MyPrioMailbox(settings: ActorSystem.Settings, config: Config) extends UnboundedPriorityMailbox(
    // Create a new PriorityGenerator, lower prio means more important
    PriorityGenerator {
      // 'highpriority messages should be treated first if possible
      case q:Spider ⇒ {
//        println("mailbox:"+q.path.size)
        q.path.size
      }

      case otherwise     ⇒ 1
    })
