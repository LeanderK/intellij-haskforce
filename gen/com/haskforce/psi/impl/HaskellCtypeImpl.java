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

public class HaskellCtypeImpl extends HaskellCompositeElementImpl implements HaskellCtype {

  public HaskellCtypeImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof HaskellVisitor) ((HaskellVisitor)visitor).visitCtype(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public HaskellClscontext getClscontext() {
    return findChildByClass(HaskellClscontext.class);
  }

  @Override
  @Nullable
  public HaskellCtype getCtype() {
    return findChildByClass(HaskellCtype.class);
  }

  @Override
  @NotNull
  public List<HaskellTvBndr> getTvBndrList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, HaskellTvBndr.class);
  }

  @Override
  @Nullable
  public HaskellTypee getTypee() {
    return findChildByClass(HaskellTypee.class);
  }

  @Override
  @Nullable
  public PsiElement getForalltoken() {
    return findChildByType(FORALLTOKEN);
  }

  @Override
  @Nullable
  public PsiElement getDoublearrow() {
    return findChildByType(DOUBLEARROW);
  }

  @Override
  @Nullable
  public PsiElement getPeriod() {
    return findChildByType(PERIOD);
  }

}
