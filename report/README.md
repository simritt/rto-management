# RTO Management System: mini-project report (LaTeX)

Entry point: `main.tex`. A compiled copy is in `main.pdf`.

## Build
- Local: `pdflatex main` three times (the third run settles the table of contents and cross-references).
- Overleaf: upload `RTO_Management_Report_Overleaf.zip`, set `main.tex` as the main file, compiler pdfLaTeX.

## Layout
- `frontmatter/`: cover, certificates, declaration, acknowledgement, abstract, abbreviations
- `chapters/`: chapters 1 to 11, references, appendices
- `generated/`: appendix tables produced from the live database and the API description
- `figures/screens/`: six screenshots; `figures/diagrams/`: architecture, UML, DFD and ER diagrams (sources in `figures/src/`)
- `data/`: raw measurements behind chapters 8 and 9 (not needed to compile)

## Before submission
Team details, guide name and group number are constants near the top of `main.tex`.
