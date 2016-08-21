package com.haskforce.codeInsight

import com.intellij.codeInsight.lookup.LookupElement

/**
  * this class is returned from GHCMod when browsing a module
  * @param name the name of the module
  * @param module
  * @param typ
  */
case class BrowseItem(name: String, module: String, typ: String) {
  def toLookupElement: LookupElement = LookupElementUtil.fromBrowseItem(this)
}
