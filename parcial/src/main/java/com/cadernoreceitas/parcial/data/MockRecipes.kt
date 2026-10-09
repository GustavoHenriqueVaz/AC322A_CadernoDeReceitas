package com.cadernoreceitas.parcial.data

import com.cadernoreceitas.parcial.model.Difficulty
import com.cadernoreceitas.parcial.model.Ingredient
import com.cadernoreceitas.parcial.model.Recipe
import com.cadernoreceitas.parcial.model.RecipeCategory

/**
 * Dados simulados (mocks): nesta etapa nao ha API nem banco de dados.
 * Algumas receitas deixam `tip`/`videoUrl`/`quantity` nulos de proposito
 * para exercitar o tratamento de valores opcionais na interface.
 */
object MockRecipes {

    val all: List<Recipe> = listOf(
        Recipe(
            id = 1,
            name = "Panqueca de banana",
            category = RecipeCategory.BREAKFAST,
            prepMinutes = 15,
            servings = 2,
            difficulty = Difficulty.EASY,
            ingredients = listOf(
                Ingredient("Banana madura", "2 unidades"),
                Ingredient("Ovo", "2 unidades"),
                Ingredient("Aveia em flocos", "4 colheres de sopa"),
                Ingredient("Canela em pó", "1 pitada"),
                Ingredient("Óleo ou manteiga para untar"),
            ),
            steps = listOf(
                "Amasse as bananas com um garfo até virar um purê.",
                "Misture os ovos, a aveia e a canela até formar uma massa homogênea.",
                "Aqueça uma frigideira antiaderente untada em fogo médio.",
                "Despeje uma concha de massa e doure por 2 minutos de cada lado.",
            ),
            tip = "Quanto mais madura a banana, mais doce fica a panqueca.",
            videoUrl = "https://www.youtube.com/results?search_query=panqueca+de+banana",
        ),
        Recipe(
            id = 2,
            name = "Macarrão alho e óleo",
            category = RecipeCategory.MAIN,
            prepMinutes = 20,
            servings = 2,
            difficulty = Difficulty.EASY,
            ingredients = listOf(
                Ingredient("Espaguete", "250 g"),
                Ingredient("Alho laminado", "4 dentes"),
                Ingredient("Azeite de oliva", "5 colheres de sopa"),
                Ingredient("Pimenta calabresa", "a gosto"),
                Ingredient("Salsinha picada"),
                Ingredient("Sal"),
            ),
            steps = listOf(
                "Cozinhe o espaguete em água com sal até ficar al dente.",
                "Doure o alho no azeite em fogo baixo, sem deixar queimar.",
                "Junte a pimenta e um pouco da água do cozimento da massa.",
                "Misture o macarrão ao molho e finalize com a salsinha.",
            ),
            tip = "Guarde uma xícara da água do cozimento: ela deixa o molho cremoso.",
        ),
        Recipe(
            id = 3,
            name = "Salada caprese",
            category = RecipeCategory.SALAD,
            prepMinutes = 10,
            servings = 2,
            difficulty = Difficulty.EASY,
            ingredients = listOf(
                Ingredient("Tomate maduro", "3 unidades"),
                Ingredient("Muçarela de búfala", "200 g"),
                Ingredient("Folhas de manjericão", "1 punhado"),
                Ingredient("Azeite extravirgem", "2 colheres de sopa"),
                Ingredient("Sal e pimenta-do-reino"),
            ),
            steps = listOf(
                "Corte os tomates e a muçarela em fatias de mesma espessura.",
                "Intercale as fatias em um prato e distribua o manjericão.",
                "Tempere com azeite, sal e pimenta na hora de servir.",
            ),
        ),
        Recipe(
            id = 4,
            name = "Brigadeiro de panela",
            category = RecipeCategory.DESSERT,
            prepMinutes = 25,
            servings = 12,
            difficulty = Difficulty.MEDIUM,
            ingredients = listOf(
                Ingredient("Leite condensado", "1 lata"),
                Ingredient("Chocolate em pó", "4 colheres de sopa"),
                Ingredient("Manteiga", "1 colher de sopa"),
                Ingredient("Chocolate granulado", "para enrolar"),
            ),
            steps = listOf(
                "Leve ao fogo baixo o leite condensado, o chocolate e a manteiga.",
                "Mexa sem parar até o brigadeiro desgrudar do fundo da panela.",
                "Espere esfriar, faça bolinhas e passe no granulado.",
            ),
            tip = "Unte as mãos com manteiga para enrolar sem grudar.",
            videoUrl = "https://www.youtube.com/results?search_query=brigadeiro+de+panela",
        ),
        Recipe(
            id = 5,
            name = "Sopa de abóbora com gengibre",
            category = RecipeCategory.SOUP,
            prepMinutes = 40,
            servings = 4,
            difficulty = Difficulty.MEDIUM,
            ingredients = listOf(
                Ingredient("Abóbora cabotiá", "600 g"),
                Ingredient("Cebola", "1 unidade"),
                Ingredient("Gengibre ralado", "1 colher de chá"),
                Ingredient("Caldo de legumes", "1 litro"),
                Ingredient("Creme de leite", "3 colheres de sopa"),
            ),
            steps = listOf(
                "Refogue a cebola e o gengibre em um fio de azeite.",
                "Adicione a abóbora em cubos e o caldo; cozinhe por 25 minutos.",
                "Bata no liquidificador e volte à panela com o creme de leite.",
                "Acerte o sal e sirva quente.",
            ),
            tip = "Sirva com sementes de abóbora torradas por cima.",
        ),
        Recipe(
            id = 6,
            name = "Omelete de queijo e tomate",
            category = RecipeCategory.BREAKFAST,
            prepMinutes = 10,
            servings = 1,
            difficulty = Difficulty.EASY,
            ingredients = listOf(
                Ingredient("Ovos", "3 unidades"),
                Ingredient("Queijo minas ralado", "3 colheres de sopa"),
                Ingredient("Tomate sem sementes", "1/2 unidade"),
                Ingredient("Orégano"),
                Ingredient("Sal"),
            ),
            steps = listOf(
                "Bata os ovos com uma pitada de sal.",
                "Despeje em frigideira untada e espalhe o queijo e o tomate.",
                "Dobre ao meio quando as bordas firmarem e sirva.",
            ),
        ),
        Recipe(
            id = 7,
            name = "Frango grelhado com legumes",
            category = RecipeCategory.MAIN,
            prepMinutes = 45,
            servings = 3,
            difficulty = Difficulty.MEDIUM,
            ingredients = listOf(
                Ingredient("Filé de frango", "500 g"),
                Ingredient("Abobrinha", "1 unidade"),
                Ingredient("Cenoura", "2 unidades"),
                Ingredient("Limão", "1 unidade"),
                Ingredient("Alho amassado", "2 dentes"),
                Ingredient("Azeite, sal e ervas"),
            ),
            steps = listOf(
                "Tempere o frango com limão, alho, sal e ervas por 20 minutos.",
                "Grelhe os filés até dourarem dos dois lados.",
                "Salteie os legumes em tiras no azeite.",
                "Sirva o frango com os legumes ao lado.",
            ),
            tip = "Deixe o frango descansar 3 minutos antes de cortar para ficar suculento.",
            videoUrl = "https://www.youtube.com/results?search_query=frango+grelhado+com+legumes",
        ),
        Recipe(
            id = 8,
            name = "Pudim de leite",
            category = RecipeCategory.DESSERT,
            prepMinutes = 90,
            servings = 8,
            difficulty = Difficulty.HARD,
            ingredients = listOf(
                Ingredient("Leite condensado", "1 lata"),
                Ingredient("Leite integral", "1 lata (medida da lata)"),
                Ingredient("Ovos", "3 unidades"),
                Ingredient("Açúcar para a calda", "1 xícara"),
            ),
            steps = listOf(
                "Faça a calda derretendo o açúcar e espalhe na forma.",
                "Bata no liquidificador o leite condensado, o leite e os ovos.",
                "Despeje na forma, cubra com papel-alumínio e asse em banho-maria por 1 hora.",
                "Leve à geladeira por 4 horas antes de desenformar.",
            ),
        ),
    )

    fun byId(id: Int): Recipe? = all.firstOrNull { it.id == id }
}
