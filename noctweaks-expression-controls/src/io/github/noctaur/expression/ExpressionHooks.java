package io.github.noctaur.expression;

import bitwig.obf.*;
import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.This;
import com.bitwig.flt.widget.core.timeline.interaction.MoveValueEventsDragAction; // dragging points or notes
import com.bitwig.flt.widget.core.timeline.ju1;                                    // a note editor's content widget
import com.bitwig.graphics.ijY;                                                    // a graphics context

import java.util.List;

/** Every hook of Piano Roll: Expression Controls. The logic is in the Noc* classes. */
public final class ExpressionHooks {
   private ExpressionHooks() {
   }

   // ------------------------------------------------------------------ icons

   /** dou.beI(String, double): a window loads an icon by name (from its cache, else from Bitwig's files). */
   @Hook(target = dou.class, method = "beI(Ljava/lang/String;D)Lcom/bitwig/graphics/haN;", at = At.ENTRY, id = "icons")
   public static void icon(@This dou window, @Arg(0) String name, @Arg(1) double scale) {
      Icons.loading(window, name, scale);
   }

   // ------------------------------------------------------------------ edit modes always visible

   /** dDG: an option button; beI(ijY) paints it. The editor's edit-mode button gets the strip beside it. */
   @Hook(target = dDG.class, method = "beI(Lcom/bitwig/graphics/ijY;)V", at = At.ENTRY, id = "edit-modes")
   public static void editModes(@This dDG button) {
      try {
         NocModes.drawn(button);
      } catch (Throwable t) {
         Expr.log("edit modes", t);
      }
   }

   // ------------------------------------------------------------------ expression shapes

   /**
    * aOp.beI(dMt, ju1, rBG): the note editor's right-click menu. Its arguments aren't readable at its return
    * (the verifier's frame there holds only this), so the entry hook keeps them for the one at the end.
    */
   private static final ThreadLocal<Object[]> MENU_ARGS = new ThreadLocal<>();

   @Hook(target = aOp.class, method = "beI(LdMt;Lcom/bitwig/flt/widget/core/timeline/ju1;LrBG;)V", at = At.ENTRY, id = "shapes-menu-args")
   public static void shapesMenuArgs(@Arg(0) dMt menu, @Arg(1) ju1 target, @Arg(2) rBG selection) {
      MENU_ARGS.set(new Object[]{menu, target, selection});
   }

   @Hook(target = aOp.class, method = "beI(LdMt;Lcom/bitwig/flt/widget/core/timeline/ju1;LrBG;)V", at = At.BEFORE_RETURN, id = "shapes-menu")
   public static void shapesMenu(@This aOp editor) {
      Object[] a = MENU_ARGS.get();
      MENU_ARGS.remove();
      try {
         if (a != null) {
            NocShapes.menu((dMt) a[0], editor, a[1], a[2]);
         }
      } catch (Throwable t) {
         Expr.log("shapes menu", t);
      }
   }

   /** bmh.beI(dMt, dwF, bju, yAn): the menu for selected notes and expression points. */
   @Hook(target = bmh.class, method = "beI(LdMt;LdwF;Lbju;LyAn;)V", at = At.BEFORE_RETURN, id = "shapes-selection-menu")
   public static void shapesSelectionMenu(@Arg(0) dMt menu, @Arg(1) dwF owner, @Arg(2) bju header) {
      try {
         NocShapes.selectionMenu(menu, owner, header);
      } catch (Throwable t) {
         Expr.log("shapes selection menu", t);
      }
   }

   /** aMo.b_(dXL): the Inspector's note expression section is built. */
   @Hook(target = aMo.class, method = "b_(LdXL;)V", at = At.BEFORE_RETURN, id = "shape-panel")
   public static void shapePanel(@This aMo panel, @Arg(0) dXL box) {
      try {
         NocShapePanel.inspector(panel, box);
      } catch (Throwable t) {
         Expr.log("shape panel", t);
      }
   }

   /** aHH.beI(dXL): the Points Inspector is built. */
   @Hook(target = aHH.class, method = "beI(LdXL;)V", at = At.BEFORE_RETURN, id = "shape-points")
   public static void shapePoints(@This aHH panel, @Arg(0) dXL box) {
      try {
         NocShapePanel.points(panel, box);
      } catch (Throwable t) {
         Expr.log("shape points", t);
      }
   }

   // ------------------------------------------------------------------ piano roll, not the drum grid

   /**
    * Q3V.beI(AWo, HSH): the note editor picks its editor for new content; VXQ.y3f() is the drum grid
    * candidate, offered when the track has a drum machine. Leaving it out opens the piano roll. Picking the
    * grid by hand still works.
    */
   @Hook(target = Q3V.class, method = "beI(LAWo;LHSH;)V", at = At.REPLACE_CALL, callOwner = VXQ.class, call = "y3f()LFGV;", id = "piano-roll-default")
   public static FGV drumGrid(VXQ editors) {
      return null;
   }

   // ------------------------------------------------------------------ Ctrl+drag copies expression points

   private static final String M = "com/bitwig/flt/widget/core/timeline/interaction/MoveValueEventsDragAction";
   private static final ThreadLocal<Boolean> MINE = new ThreadLocal<>();

   @Hook(target = MoveValueEventsDragAction.class, method = "beginEventEditing(LdLi;Lcom/bitwig/ramona/core/IqI;Ljava/util/List;)V",
         at = At.ENTRY, id = "copy-drag-begin")
   public static void copyDragBegin(@This MoveValueEventsDragAction action, @Arg(0) dLi event, @Arg(2) List<?> events) {
      try {
         NocCopyDrag.begin(action, event, events);
      } catch (Throwable t) {
         Expr.log("copy drag", t);
      }
   }

   /** Each mouse move: while the copy is drawn, Bitwig's own move of the points is left out. */
   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.ENTRY, id = "copy-drag-update")
   public static void copyDragUpdate(@This MoveValueEventsDragAction action, @Arg(0) double dt, @Arg(1) double dv) {
      boolean mine = false;
      try {
         mine = NocCopyDrag.update(action, dt, dv);
      } catch (Throwable t) {
         Expr.log("copy drag", t);
      }
      MINE.set(mine);
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.REPLACE_CALL, callOwner = vVA.class, call = "beI(Z)V", id = "copy-drag-boundary")
   public static void moveBoundary(vVA op, boolean boundary) {
      if (!mine()) {
         op.beI(boundary); // create boundary points
      }
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.REPLACE_CALL, callOwner = vVA.class, call = "beI(D)V", id = "copy-drag-time")
   public static void moveTime(vVA op, double dt) {
      if (!mine()) {
         op.beI(dt); // the time offset
      }
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.REPLACE_CALL, callOwner = vVA.class, call = "Gef(D)V", id = "copy-drag-value")
   public static void moveValue(vVA op, double dv) {
      if (!mine()) {
         op.Gef(dv); // the value offset
      }
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "updateEditEvents", at = At.REPLACE_CALL, callOwner = vVA.class, call = "unz()V", id = "copy-drag-apply")
   public static void moveApply(vVA op) {
      if (!mine()) {
         op.unz(); // apply the move
      }
   }

   private static boolean mine() {
      return Boolean.TRUE.equals(MINE.get());
   }

   /** On release: the copy is written; Bitwig's own (unused) move was cancelled, so it isn't committed. */
   @Hook(target = MoveValueEventsDragAction.class, method = "finishEventEditing", at = At.ENTRY, id = "copy-drag-finish")
   public static void copyDragFinish(@This MoveValueEventsDragAction action) {
      boolean mine = false;
      try {
         mine = NocCopyDrag.finish(action);
      } catch (Throwable t) {
         Expr.log("copy drag", t);
      }
      MINE.set(mine);
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "finishEventEditing", at = At.REPLACE_CALL, callOwner = vVA.class, call = "byf()V", id = "copy-drag-commit")
   public static void commit(vVA op) {
      try {
         if (!mine()) {
            op.byf(); // commit the move
         }
      } finally {
         MINE.remove();
      }
   }

   @Hook(target = MoveValueEventsDragAction.class, method = "cancelEventEditing", at = At.ENTRY, id = "copy-drag-cancel")
   public static void copyDragCancel(@This MoveValueEventsDragAction action) {
      try {
         NocCopyDrag.cancel(action);
      } catch (Throwable t) {
         Expr.log("copy drag", t);
      }
   }
}
