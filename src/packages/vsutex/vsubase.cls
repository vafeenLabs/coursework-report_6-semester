% !TEX TS-program = lualatex
\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{vsubase}[2024/04/03 1.0.0 The base class with presets for Voronezh state university]

% Passtrow all options for class to extarticle class
\DeclareOption*{\PassOptionsToClass{\CurrentOption}{extarticle}}
\ProcessOptions\relax

% Set A4 paper
\LoadClass[a4paper]{extarticle}

% Set Russian as main document language
\RequirePackage[english, russian]{babel}

% Set page padding
\RequirePackage[left=3cm, right=1.5cm, top=2cm, bottom=2cm]{geometry}

% Set Times New Roman font
\RequirePackage{fontspec}
\setmainfont{Times New Roman}

% Set 0pt space after paragraphs
\RequirePackage{parskip}
\setlength{\parskip}{0pt}

% Set first line 1.25cm indent
\RequirePackage{indentfirst}
\setlength{\parindent}{1.25cm}

% Set dynamic spaces between words
\sloppy

% Set section styles
\RequirePackage{titlesec}
\titleformat{\section}{\normalfont\normalsize\centering\bfseries}{\thesection. }{0pt}{}

% Add hyperrefs to the document
\RequirePackage{hyperref}

% Replace : to . in captions
\RequirePackage[labelsep=period]{caption}

% Set gost style for bibliography
\bibliographystyle{pugost2008}

% Add ability to use & in equations properly
\RequirePackage{amsmath}

% Internal packages
\RequirePackage{environ}
\RequirePackage{vsutex}

% Set current year by default
\makeatletter
\renewcommand{\@date}{\the\year}
\makeatother

% Add algorithms and translate it to Russian
\RequirePackage{algorithm}
\RequirePackage{algpseudocode}
\makeatletter

\renewcommand{\ALG@name}{Алгоритм}
\algrenewcommand\algorithmicend{\textbf{конец}}
\algrenewcommand\algorithmicdo{\textbf{делать}}
\algrenewcommand\algorithmicwhile{\textbf{пока}}
\algrenewcommand\algorithmicfor{\textbf{для}}
\algrenewcommand\algorithmicforall{\textbf{для всех}}
\algrenewcommand\algorithmicloop{\textbf{цикл}}
\algrenewcommand\algorithmicrepeat{\textbf{повторить}}
\algrenewcommand\algorithmicuntil{\textbf{пока не}}
\algrenewcommand\algorithmicprocedure{\textbf{процедура}}
\algrenewcommand\algorithmicfunction{\textbf{функция}}
\algrenewcommand\algorithmicif{\textbf{если}}
\algrenewcommand\algorithmicthen{\textbf{то}}
\algrenewcommand\algorithmicelse{\textbf{иначе}}
\algrenewcommand\algorithmicrequire{\textbf{Вход:}}
\algrenewcommand\algorithmicensure{\textbf{Выход:}}
\algrenewcommand\algorithmicreturn{\textbf{возврат}}

\makeatother
