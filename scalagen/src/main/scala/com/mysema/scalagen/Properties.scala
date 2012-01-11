/*
 * Copyright (C) 2011, Mysema Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */
package com.mysema.scalagen

import japa.parser.ast.CompilationUnit
import japa.parser.ast.body.ModifierSet
import japa.parser.ast.body.VariableDeclarator
import japa.parser.ast.body.ClassOrInterfaceDeclaration
import japa.parser.ast.`type`.PrimitiveType
import japa.parser.ast.`type`.PrimitiveType.Primitive
import japa.parser.ast.`type`.VoidType
import japa.parser.ast.stmt.{ExpressionStmt, IfStmt, Statement}
import java.util.ArrayList
import com.mysema.scala.BeanUtils
import UnitTransformer._

object Properties extends Properties

/**
 * Properties turns field + accessor combinations into annotated 
 * Scala properties
 */
class Properties extends UnitTransformerBase {
    
  def transform(cu: CompilationUnit): CompilationUnit = {
    cu.accept(this, cu).asInstanceOf[CompilationUnit] 
  }  
  
  override def visit(n: ClassOrInterface, cu: CompilationUnit): ClassOrInterface = {      
    val t = super.visit(n, cu).asInstanceOf[ClassOrInterface]
    
    // accessors
    val getters = t.getMembers.collect { case m: Method => m }
      .filter(m => isGetter(m))
      .map(m => (m.getName,m)).toMap      
    
    // fields with accessors
    val fields = t.getMembers.collect { case f: Field => f }
      .filter(_.getModifiers.isPrivate)
      .flatMap( f => f.getVariables.map( v => (v.getId.getName,v,f) ))
      .filter { case (name,_,_) =>  getters.contains(name) }
          
    // remove accessors 
    for ( (name,variable,field) <- fields) {
      var getter = getters(name)
      val body = getter.getBody
      if (isReturnFieldStmt(body(0))) {
        t.getMembers.remove(getter)
        field.setModifiers(getter.getModifiers)
      } else if (isLazyCreation(body,name)) {
        t.getMembers.remove(getter)
        // TODO : extract via match
        val init = body(0).asInstanceOf[IfStmt]
          .getThenStmt.asInstanceOf[Block]
          .getStmts().get(0).asInstanceOf[ExpressionStmt]
          .getExpression.asInstanceOf[Assign]
          .getValue        
        variable.setInit(init)
        field.setModifiers(getter.getModifiers
          .addModifier(LAZY).addModifier(ModifierSet.FINAL))
      }            
    }    
    t
  }
  
  // if (uncreated) { create } return
  private def isLazyCreation(block: Block, f: String): Boolean = block match {
    case Block(
        If(isnull(field(f1)), Block(Stmt(field(f2) assign init) :: Nil), null) :: 
        Return(field(f3)) :: Nil) 
        if f1 == f && f2 == f && f3 == f => true
    case _ => false   
  }
  
  private def isGetter(method: Method): Boolean = method match {
    case Method(n, t, Nil, Block(_ :: _)) if !t.isInstanceOf[VoidType] => true
    case _ => false
  }    
    
//  private def isGetter(method: Method): Boolean = { 
//    isEmpty(method.getParameters) && 
//    !method.getType.isInstanceOf[VoidType] &&
//    !method.getBody.isEmpty
//    
//  }
  
}
