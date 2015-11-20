package com.haskforce

import java.awt.event._
import java.util.Comparator
import javax.swing.event.{DocumentListener, DocumentEvent}

import com.intellij.openapi.util.{Computable, Condition}

object Implicits {
  implicit def funToRunnable(block: => Unit): Runnable = new Runnable { def run() = block }

  implicit def funToComputable[A](block: => A): Computable[A] = new Computable[A] { def compute(): A = block }

  implicit def funToItemListener(f: ItemEvent => Unit): ItemListener = new ItemListener {
    override def itemStateChanged(e: ItemEvent): Unit = f(e)
  }

  implicit class FunToKeyListener(f: KeyEvent => Unit) extends KeyAdapter {
    override def keyReleased(e: KeyEvent): Unit = f(e)
  }

  implicit def funToActionListener(f: ActionEvent => Unit): ActionListener = new ActionListener {
    override def actionPerformed(e: ActionEvent): Unit = f(e)
  }

  implicit class FunToDocumentListener(f: DocumentEvent => Unit) extends DocumentListener {
    override def insertUpdate(e: DocumentEvent): Unit = f(e)
    override def changedUpdate(e: DocumentEvent): Unit = f(e)
    override def removeUpdate(e: DocumentEvent): Unit = f(e)
  }

  implicit def funToCondition[A](f: A => Boolean): Condition[A] = new Condition[A] {
    override def value(t: A): Boolean = f(t)
  }

  implicit def funToComparator[A](f: (A, A) => Int): Comparator[A] = new Comparator[A] {
    override def compare(o1: A, o2: A): Int = f(o1, o2)
  }

  implicit class BooleanToOption(b: Boolean) {
    def option[A](a: => A): Option[A] = if (b) Some(a) else None
  }

  /**
   * Provides a .nullMap() method for Option which wraps potentially null results in an Option.
   * Example:
   *   foo.getBar().getBaz() <- one of these might be null
   *   Option(foo).nullMap(_.getBar()).nullMap(_.getBaz()) <- returns None if any value was null.
   */
  // TODO: Should we create Scala interfaces around the Java ones to replace null with Option entirely?
  implicit class ToOptionNullMap[A](private val o: Option[A]) extends AnyVal {
    def nullMap[B](f: A => B): Option[B] = o.flatMap(a => Option(f(a)))
  }
}
