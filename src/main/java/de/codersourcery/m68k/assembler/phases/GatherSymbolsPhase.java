package de.codersourcery.m68k.assembler.phases;

import de.codersourcery.m68k.assembler.ICompilationContext;
import de.codersourcery.m68k.assembler.ICompilationPhase;
import de.codersourcery.m68k.assembler.Symbol;
import de.codersourcery.m68k.assembler.SymbolTable;
import de.codersourcery.m68k.parser.ast.AST;
import de.codersourcery.m68k.parser.ast.ASTNode;
import de.codersourcery.m68k.parser.ast.IASTNode;
import de.codersourcery.m68k.parser.ast.LabelNode;
import de.codersourcery.m68k.parser.ast.NodeType;

import java.util.function.BiConsumer;

/**
 * Compilation phase that gathers all symbols.
 */
public class GatherSymbolsPhase implements ICompilationPhase
{
    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        final AST ast = ctx.getCompilationUnit().getAST();
        final SymbolTable table = ctx.symbolTable();
        final BiConsumer<ASTNode, IASTNode.IterationCtx<Object>> consumer =
                (node,itCtx) ->
                {
                  if ( node.is(NodeType.LABEL ) )
                  {
                      LabelNode label = node.asLabel();
                      final Symbol s = new Symbol(label.getValue().identifier,
                      Symbol.SymbolType.LABEL);
                      s.setDeclaration(node);
                      try
                      {
                          table.define(s);
                      } catch(Exception e) {
                          ctx.error("Failed to define label "+label.getValue()+": "+e.getMessage(),label);
                      }
                  }
                };
        ast.visitInOrder(consumer);
    }
}