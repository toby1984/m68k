package de.codesourcery.m68k.assembler.phases;

import de.codesourcery.m68k.assembler.ICompilationContext;
import de.codesourcery.m68k.assembler.ICompilationPhase;
import de.codesourcery.m68k.assembler.Symbol;
import de.codesourcery.m68k.parser.ast.ASTNode;
import de.codesourcery.m68k.parser.ast.DirectiveNode;
import de.codesourcery.m68k.parser.ast.IASTNode;
import de.codesourcery.m68k.parser.ast.ICodeGeneratingNode;
import de.codesourcery.m68k.parser.ast.StatementNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class CodeGenerationPhase implements ICompilationPhase
{
    private static final Logger LOG = LogManager.getLogger( CodeGenerationPhase.class.getName() );

    public boolean estimateSizeForUnknownOperands;
    private final Function<ICompilationContext,Boolean> shouldRun;

    public CodeGenerationPhase(boolean estimateSizeForUnknownOperands)
    {
        this(estimateSizeForUnknownOperands,ctx -> Boolean.TRUE);
    }

    public CodeGenerationPhase(boolean estimateSizeForUnknownOperands,Function<ICompilationContext,Boolean> shouldRun)
    {
        this.estimateSizeForUnknownOperands = estimateSizeForUnknownOperands;
        this.shouldRun = shouldRun;
    }

    @Override
    public boolean shouldRun(ICompilationContext ctx)
    {
        return this.shouldRun.apply(ctx);
    }

    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        ctx.getCodeWriter().reset();

        final boolean debug = ctx.isDebugModeEnabled();

        final BiConsumer<IASTNode, ASTNode.IterationCtx<Void>> visitor = (node, itCtx) ->
        {
            if ( node instanceof DirectiveNode )
            {
                final DirectiveNode dn = (DirectiveNode) node;
                if ( dn.directive == DirectiveNode.Directive.ORG)
                {
                    ctx.getCodeWriter().setOffset( dn.getOrigin() );
                }
            }
            else if ( node instanceof StatementNode)
            {
                final StatementNode stmt = (StatementNode) node;
                for ( var label : stmt.getLabels() )
                {
                    final Symbol symbol = ctx.symbolTable().lookup(label.getValue().identifier);
                    final int offset = ctx.getCodeWriter().offset();
                    if ( debug )
                    {
                        LOG.info( "Assigning offset $" + Integer.toHexString(offset) + " to label '" + symbol.identifier + "'" );
                    }
                    symbol.setValue(offset);
                }
            }
            else if ( node instanceof ICodeGeneratingNode)
            {
                ((ICodeGeneratingNode) node).generateCode(ctx, estimateSizeForUnknownOperands);
            }
        };
        ctx.getCompilationUnit().getAST().visitInOrder(visitor);
    }

    @Override
    public String toString()
    {
        return "code generation (first_pass="+ estimateSizeForUnknownOperands +")";
    }
}