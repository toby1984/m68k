package de.codesourcery.m68k.assembler.phases;

import de.codesourcery.m68k.assembler.ICompilationContext;
import de.codesourcery.m68k.assembler.ICompilationPhase;
import de.codesourcery.m68k.parser.Lexer;
import de.codesourcery.m68k.parser.Parser;
import de.codesourcery.m68k.parser.StringScanner;
import de.codesourcery.m68k.parser.ast.AST;

public class ParsePhase implements ICompilationPhase
{
    @Override
    public void run(ICompilationContext ctx) throws Exception
    {
        final Parser p = new Parser();
        final String source = ctx.getSource(ctx.getCompilationUnit());
        final Lexer lexer = new Lexer(new StringScanner(source));
        final AST ast = p.parse(lexer);
        ctx.getCompilationUnit().setAST(ast);
    }

    @Override
    public String toString()
    {
        return "Parse phase";
    }
}
