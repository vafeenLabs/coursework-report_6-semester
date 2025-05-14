% !TEX TS-program = lualatex
\NeedsTeXFormat{LaTeX2e}
\ProvidesClass{ammstudconf}[2024/04/03 1.0.0 The class with report presents for Voronezh state university]

% Passtrow all options for class to vsubase class
\DeclareOption*{\PassOptionsToClass{\CurrentOption}{vsubase}}
\ProcessOptions\relax

% Load base class
\LoadClass[12pt]{vsubase}

% Set section styles
\titleformat{\subsection}{\normalfont\normalsize\centering\bfseries\itshape}{\thesubsection. }{0pt}{}
\titleformat{\subsubsection}{\normalfont\normalsize\centering\bfseries\itshape}{\thesubsubsection. }{0pt}{}

% Replace bullet to dashes in lists
\let\labelitemi\labelitemii

% Set headers
\makeatletter

% Set conference name
\newcommand{\@confname}[0]{Конференция школьников, студентов и молодых ученых}
\newcommand{\confname}[1]{\renewcommand{\@confname}{#1}}

\RequirePackage{fancyhdr}
\RequirePackage{color}
\pagestyle{fancy}
\setlength{\headheight}{15pt}
\chead{\textcolor{red}{\@confname}}
\lhead{}
\rhead{}
\cfoot{\thepage}
\renewcommand{\headrulewidth}{0pt}
\renewcommand{\footrulewidth}{0pt}
\makeatother

% Rename bibliography section and ignore hbadness
\makeatletter
\let\@oldbibliography\bibliography
\let\@oldrefname\refname
\renewcommand{\bibliography}[1]{%
    \renewcommand{\refname}{Литература}
    \let\@oldhbadness\hbadness
    \hbadness 10000
    \@oldbibliography{#1}
    \let\hbadness\@oldhbadness
    \let\@oldhbadness\undefine
    \let\refname\@oldrefname
    \let\@oldrefname\undefine
}
\makeatother

% Add contacts information
% \contact{fullname}{info}{email}
\newcommand{\contact}[3]{\vspace{1em}\textbf{#1} --- #2. E-mail: #3}

% Set title style
\makeatletter

% Set universal decimal classification
\newcommand{\@udk}[0]{\PackageError{vsureport}{Set udk before the using maketitle}}
\newcommand{\udk}[1]{\renewcommand{\@udk}{#1}}

% Set university
\newcommand{\@university}[0]{Воронежский государственный университет}
\newcommand{\university}[1]{\renewcommand{\@university}{#1}}

% Patch maketitle command and ignore hbox
\long\def\@maketitle{%
    \let\@oldhfuzz\hfuzz
    \hfuzz 10000pt

    УДК \@udk \vspace{2em}

    \begin{center}
        \textbf{\@title}\vspace{1em}

        \textbf{\@author}\vspace{1em}

        \textit{\@university}
    \end{center}

    \let\hfuzz\@oldhfuzz
    \let\@oldhfuzz\undefine
}

% Patch maketitle for using fancyhdr style
\let\@oldmaketitle\maketitle
\renewcommand{\maketitle}{\@oldmaketitle\thispagestyle{fancy}}

\makeatother
