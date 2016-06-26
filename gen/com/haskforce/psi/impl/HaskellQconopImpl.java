// This is a generated file. Not intended for manual editing.
package com.haskforce.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.haskforce.psi.HaskellTypes.*;
import com.haskforce.psi.*;

public class HaskellQconopImpl extends HaskellCompositeElementImpl implements HaskellQconop {

  public HaskellQconopImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull HaskellVisitor visitor) {
    visitor.visitQconop(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof HaskellVisitor) accept((HaskellVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public HaskellGconsym getGconsym() {
    return PsiTreeUtil.getChildOfType(this, HaskellGconsym.class);
  }

  @Override
  @Nullable
  public HaskellQconid getQconid() {
    return PsiTreeUtil.getChildOfType(this, HaskellQconid.class);
  }

}
